package tray.api

import java.util.UUID

import cats.{Id, Monoid}
import cats.data.OptionT
import cats.effect.{Concurrent, Sync, Timer}
import fs2.Chunk
import io.circe.{Decoder, Json, JsonObject}
import org.http4s._
import org.http4s.headers.{Location, `Content-Type`}
import tray.api.GCStorage.Prepared
import tray.core.GCSItem
import tray.params.{ListFilter, WatchAll}
import tray.serde._
import tray.batch.Batch
import tray.core.StorageEndpoints.ObjectsEndpoints

import scala.collection.immutable.TreeMap
import scala.collection.{SortedSet, mutable}
import scala.util.Try
import cats.data.NonEmptyList


/*
  This object represents the Objects section of the GCS api.
  [[https://cloud.google.com/storage/docs/json_api/v1#objects]]

  All operations that do not carry any raw payload, support batching.
 */
object Objects {

  import cats.implicits._
  import RequestUtil._

  protected[tray] def getObjectMetadata[F[_]](item: GCSItem)(implicit G: GCStorage[F]): F[Array[Byte]] =
    ObjectsEndpoints.getAlt(item, "json") match {
      case (uri, m) => G.authedRequest(m, uri, EmptyBody)(G.unwrapToAB)
    }

  protected[tray] def getObjectSizeFallback[F[_]](item: GCSItem, endAt: Option[Long])(implicit G: GCStorage[F], S: Sync[F]) =
    OptionT.fromOption[F](endAt).getOrElseF[Long] {
      val (uriMetadata, metadata) =
        ObjectsEndpoints.getMetadata(item, "size") // Only query size

      import fs2.text._

      (G.authedRequest(metadata, uriMetadata, EmptyBody)(
        r => r.body.through(utf8Decode).compile.toList.map(_.mkString)
      ))
        .flatMap { str =>
          import io.circe.parser._

          val o: Option[Long] = decode[io.circe.JsonObject](str).toOption
            .flatMap(_.toMap.get("size"))
            .flatMap(_.asString)
            .flatMap(
              s =>
                Try {
                  s.toLong
                }.toOption
            )

          o match {
            case Some(l) => S.pure(l)
            case None =>
              S.raiseError[Long](new Exception(s"failed to parse response data ${str}"))
          }
        }
    }

  /**
   * Does a "simple" get which downloads the requested object in "one go".
   *
   * @param item The item to get.
   * [[https://cloud.google.com/storage/docs/json_api/v1/objects/get]]
   */
  def getObject[F[_]](item: GCSItem)(implicit G: GCStorage[F]): F[Array[Byte]] =
    ObjectsEndpoints.get(item) match {
      case (uri, m) => G.authedRequest(m, uri, EmptyBody)(G.unwrapToAB)
    }

  /**
   * Does a resumable upload, effectively chunking the requests. It is not parallel.
   * This function ensures that uploading the next chunk is based on the previous' amount of accepted bytes (by the api).
   *
   * @param item        The bucket/path to upload to.
   * @param chunkFactor The "factor" of size of chunks such that 256kb sized chunks will be downloaded, so this integer will determine the payload size, eg 256kb * chunkFactor.
   * @param onFailure   The handler for failures, the parameter is the amount of bytes downloaded (also present in the returned stream). An option is to raise an error in this handler as the effect will eventually propagate up to the compiled and executed stream.
   * @param beginAt     =0 An optional parameter that specifies at what byte to start, useful for resuming a failed download or request partial data.
   * @param endAt       =None An optional parameter that specifies at what byte to end, useful for requesting the partial representation of an object, if not specified will get the object metadata first to determine the value.
   * @return A stream of data. This stream might have a raised error in it (for multiple reasons such as api errors or raising one in onFailure.
   * [[https://cloud.google.com/storage/docs/json_api/v1/objects/get]]
   */
  def getObjectChunked[F[_] : Timer](
                                      item: GCSItem,
                                      chunkFactor: Long,
                                      onFailure: Long => F[Unit],
                                      beginAt: Long = 0,
                                      endAt: Option[Long] = None
                                    )(implicit G: GCStorage[F], S: Sync[F]): fs2.Stream[F, Chunk[Byte]] = {
    val (uri, m) = ObjectsEndpoints.get(item)
    val chunkSize: Long = chunkFactor * G.baseChunkSize
    val lengthWithFallback: F[Long] = getObjectSizeFallback(item, endAt)

    /*val _ = lengthWithFallback.flatMap{ totalEnd =>
      val _ = fs2.Stream.iterateEval(beginAt -> Chunk.empty[Byte]) { case (offset, _) =>
        val end = math.min(offset + chunkSize, totalEnd - 1)
        val h = G.rangeHeader(offset, end)
        val effect = doBackoffRangedRequest[OffsetWithBody, F](m, uri, EmptyBody, start, h): F[OffsetWithBody]
        ???
      }

      ???
    }
*/
    val firstRequest: F[(OffsetWithBody, Long)] = lengthWithFallback.flatMap { end =>
      val h = G.rangeHeader(beginAt, math.min(beginAt + chunkSize, end))
      doBackoffRangedRequest[OffsetWithBody, F](m, uri, EmptyBody, beginAt, h)
        .map(r => r -> end)
    }

    val streamF: F[fs2.Stream[F, Chunk[Byte]]] = firstRequest.map {
      case (o, totalEnd) =>
        val withError: fs2.Stream[F, OffsetWithBody] = fs2.Stream
          .iterateEval(o) {
            case failed: FailedAt => S.pure(failed): F[OffsetWithBody]
            case _: DoneWithBody =>
              S.raiseError[OffsetWithBody](new Exception("Did not terminate when done with body")): F[OffsetWithBody]
            case NotDoneWithBody(offset, _) => {
              val start = offset
              val end = math.min(offset + chunkSize, totalEnd - 1)
              println(s"s $start e $end te $totalEnd o $offset")

              val h = G.rangeHeader(start, end)
              val effect = doBackoffRangedRequest[OffsetWithBody, F](m, uri, EmptyBody, start, h): F[OffsetWithBody]

              if (end == totalEnd - 1) {
                effect.map {
                  case NotDoneWithBody(_, body) => DoneWithBody(body)
                  case x => x
                }
              } else {
                effect
              }
            }
          }
          .takeWhile({
            case _: NotDoneWithBody => true
            case _ => false
          }, takeFailure = true)

        withError
          .evalMap {
            case FailedAt(fa) =>
              onFailure(fa).as(Option.empty[Chunk[Byte]]): F[Option[Chunk[Byte]]]
            case DoneWithBody(body) =>
              S.pure(Some(body)): F[Option[Chunk[Byte]]]
            case NotDoneWithBody(_, body) =>
              S.pure(Some(body)): F[Option[Chunk[Byte]]]
          }
          .collect { case Some(x) => x }
    }

    fs2.Stream
      .eval(streamF)
      .flatMap(x => x)
  }

  protected[tray] implicit val byteMonoid: Monoid[Chunk[Byte]] = new Monoid[Chunk[Byte]] {
    override def empty: Chunk[Byte] = Chunk.empty[Byte]

    override def combine(x: Chunk[Byte], y: Chunk[Byte]): Chunk[Byte] = Chunk.bytes((x.toVector ++ y.toVector).toArray)
  }

  /**
   * Parallel version of [[getObjectChunked]]
   *
   * @param parallelism determines how many requests will be performed in parallel
   * [[https://cloud.google.com/storage/docs/json_api/v1/objects/get]]
   */
  def getObjectParallel[F[_] : Timer : Sync : Concurrent](item: GCSItem,
                                                          chunkFactor: Long,
                                                          parallelism: Int,
                                                          onFailure: Long => F[Unit],
                                                          beginAt: Long = 0,
                                                          endAt: Option[Long] = None
                                                         )(implicit G: GCStorage[F]): fs2.Stream[F, Chunk[Byte]] = {
    val chunkSize: Long = chunkFactor * G.baseChunkSize
    val lengthWithFallbackF: F[Long] = getObjectSizeFallback(item, endAt)
    // It is important that we materialize the list since we don't want the concurrent effects to be dependent on the previous
    val outF = lengthWithFallbackF.map { lwf =>
      val rangesF: List[(Long, Long)] = beginOffsetRange(beginAt, lwf, chunkSize).compile.toList

      // Instead of doing n=chunks chunked requests we do k=parallelism requests of k/n chunks
      fs2.Stream(rangesF: _*)
        .lift[F]
        .parEvalMap(parallelism) { case (start, end) =>
          println(s"s $start e $end")
          getObjectChunked(item, chunkFactor, onFailure, start, Some(end))
            .compile
            .foldMonoid
        }
    }
    fs2.Stream.eval(outF).flatten
  }

  /**
   * Does a "simple" put which uploads the requested object in "one go".
   *
   * @param item The bucket/path to upload to.
   * @param data The data-stream to upload.
   * [[https://cloud.google.com/storage/docs/json_api/v1/objects/insert]]
   */
  def putObject[F[_]](item: GCSItem, data: fs2.Stream[F, Byte])(implicit G: GCStorage[F], S: Sync[F]): F[Unit] =
    ObjectsEndpoints.put(item) match {
      case (uri, m) => G.authedRequest(m, uri, data)(GCStorage.raiseEffectfulBadStatus)
    }

  /**
   * Does a parallel upload with the chunking factor determining how much of the stream to be consumed before beginning a new request.
   *
   * @param item        The bucket/path to upload to.
   * @param data        The data-stream to upload.
   * @param parallelism The number of threads used to perform the upload.
   * @param chunkFactor The "factor" of size of chunks, Google cloud only allows multiples of 256kb chunks, so this integer will determine the payload size, eg 256kb * chunkFactor.
   * @param prefix      The prefix is used to name the temporary files created, if the prefix is "tmp" then the elements will be named "tmp-1", "tmp-2"...
   * [[https://cloud.google.com/storage/docs/json_api/v1/objects/insert]]
 */
  def putParallel[F[_] : Concurrent : Sync](
                                             item: GCSItem,
                                             data: fs2.Stream[F, Byte],
                                             parallelism: Int,
                                             chunkFactor: Int,
                                             prefix: String
                                           )(implicit G: GCStorage[F]): F[Unit] = {
    val rechunked: fs2.Stream[F, (Chunk[Byte], Long)] =
      data.chunkN(G.baseChunkSize * chunkFactor).zipWithIndex
    val uploaded: fs2.Stream[F, String] =
      rechunked.mapAsyncUnordered(parallelism) {
        case (c, i) =>
          val itemName = prefix + "-" + i.toString
          val asChunks: fs2.Stream[F, Byte] = fs2.Stream
            .chunk(c)
            .lift[F]
          putObject(GCSItem(item.bucket, itemName), asChunks).as(itemName)
      }

    import tray.serde.Compose._

    val formattedSources: F[List[ComposeItem]] = uploaded.compile.toList
      .map(_.map(name => tray.serde.Compose.ComposeItem(name)))

    formattedSources.flatMap { items =>
      val s = Compose(sourceObjects = items, destination = ComposeDestination(contentType = "application/json"))
      compose(item, s)
    }
  }

  /**
   * Does a resumable upload, effectively chunking the requests without parallel.
   * This function ensures that uploading the next chunk is based on the previous' amount of accepted bytes (by the api).
   *
   * @param item        The bucket/path to upload to.
   * @param data        The data-stream to upload.
   * @param chunkFactor The "factor" of size of chunks, Google cloud only allows multiples of 256kb chunks, so this integer will determine the payload size, eg 256kb * chunkFactor.
   * @param beginAt     =0 An optional offset that specifies if an offset should used as the beginning, useful for resuming partially completed uploads. Note that the stream should only contain the remainder of the upload, eg the caller should drop the beginAt bytes from the original data.
   * @return An option, if defined contains an indication of a failure and how many bytes were written such that an upload may be resumed.
   * [[https://cloud.google.com/storage/docs/json_api/v1/objects/insert]]
 */
  def putObjectChunked[F[_] : Timer](item: GCSItem, data: fs2.Stream[F, Byte], chunkFactor: Int, beginAt: Long = 0)(
    implicit G: GCStorage[F],
    S: Sync[F]
  ): OptionT[F, Long] = {
    val rechunked: fs2.Stream[F, Chunk[Byte]] =
      data.chunkN(G.baseChunkSize * chunkFactor)

    val (initialUri, initialM) = ObjectsEndpoints.initiateResumableUpload(item)

    val o = G.authedRequest(initialM, initialUri, EmptyBody) { resp =>
      // Location header has the new uri
      val h: Option[Location] = resp.headers.get(Location)

      val done = h match {
        case None =>
          S.raiseError[Option[Long]](new Exception(s"failed to find location header, got ${resp.status.toString()}"))
        case Some(loc) => {
          val uri = loc.uri
          val m: Method = ObjectsEndpoints.resumableUploadChunk

          val firsts: fs2.Stream[F, Chunk[Byte]] = rechunked.dropLast

          val completedFirsts: fs2.Stream[F, Offset] = firsts
            .fold(S.pure[OffsetWithoutBody](NotDone(beginAt))) {
              case (prevOffsetF, bytes) =>
                prevOffsetF.flatMap {
                  case Done =>
                    S.raiseError[OffsetWithoutBody](new Exception("got done when there was still work"))
                  case failed: FailedAt => S.pure(failed)
                  case NotDone(offset) =>
                    val start = offset
                    val end = bytes.size.toLong + offset - 1

                    val h = G.contentRangeHeader(start, end, None)
                    doBackoffRangedRequest[OffsetWithoutBody, F](m, uri, fs2.Stream.chunk(bytes), start, h)
                }
            }
            .evalMap(identity)

          val combined: fs2.Stream[F, OffsetWithoutBody] = completedFirsts
            .lastOr(NotDone(0)) // If there is only one chunk
            .flatMap {
              case failed: FailedAt => fs2.Stream.eval(S.pure(failed))
              case Done => fs2.Stream.empty
              case NotDone(offset) =>
                rechunked.last
                  .collect { case Some(x) => x }
                  .evalMap { bytes =>
                    val start = offset
                    val end = bytes.size.toLong + offset - 1
                    val length = Some(end + 1)

                    val h = G.contentRangeHeader(start, end, length)
                    doBackoffRangedRequest[OffsetWithoutBody, F](m, uri, fs2.Stream.chunk(bytes), start, h)
                  }
            }

          combined
            .collectFirst { case FailedAt(x) => x }
            .compile
            .last
        }
      }

      done
    }

    OptionT(o)
  }

  /**
   * Composes GCS objects, more can be found in the official GCS documentation.
   *
   * @param destinationObject The object to copy all source objects to.
   * @param compose           The compose description. It is a 1:1 representation of the documented json payload.
   * [[https://cloud.google.com/storage/docs/json_api/v1/objects/compose]]
   */
  def compose[F[_] : Sync](destinationObject: GCSItem, compose: Compose)(implicit G: GCStorage[F]): F[Unit] =
    effectfulReq(composeReq[F](destinationObject, compose))

  def composeBatch[F[_] : Sync](destinationObject: GCSItem, compose: Compose)(implicit G: GCStorage[F]): F[Batch[Unit, F]] =
    effectfulBatch(composeReq[F](destinationObject, compose))

  protected[tray] def composeReq[F[_]](destinationObject: GCSItem, compose: Compose): Prepared[F] = {
    import fs2.text._
    import io.circe.generic.auto._
    import io.circe.syntax._
    val (uri, m) = ObjectsEndpoints.compose(destinationObject)
    GCStorage.makeRequest(m, uri, fs2.Stream(compose.asJson.noSpaces).through(utf8Encode), `Content-Type`(MediaType.application.json))
  }

  /**
   * Performs a delete that runs in one http call.
   * [[https://cloud.google.com/storage/docs/json_api/v1/objects/delete]]
   */
  def delete[F[_] : Sync](item: GCSItem)(implicit G: GCStorage[F]): F[Unit] =
    effectfulReq(deleteReq[F](item))

  def deleteBatch[F[_] : Sync](item: GCSItem)(implicit G: GCStorage[F]): F[Batch[Unit, F]] =
    effectfulBatch(deleteReq[F](item))

  protected[tray] def deleteReq[F[_]](item: GCSItem): Prepared[F] = {
    val (uri, method) = ObjectsEndpoints.delete(item)
    GCStorage.makeRequest[F](method, uri, EmptyBody)
  }

  /**
   * Performs a server-side GCS copy that runs in one http call.
   * [[https://cloud.google.com/storage/docs/json_api/v1/objects/copy]]
   */
  def copy[F[_] : Sync](from: GCSItem, to: GCSItem)(implicit G: GCStorage[F]): F[Unit] =
    effectfulReq(copyReq[F](from, to))

  def copyBatch[F[_] : Sync](from: GCSItem, to: GCSItem)(implicit G: GCStorage[F]): F[Batch[Unit, F]] =
    effectfulBatch(copyReq[F](from, to))

  protected[tray] def copyReq[F[_] : Sync](from: GCSItem, to: GCSItem): Prepared[F] = {
    val (uri, method) = ObjectsEndpoints.copy(from, to)
    GCStorage.makeRequest[F](method, uri, EmptyBody)
  }

  private def listingBodyHandler[F[_], T: Decoder](r: Response[F])(implicit S: Sync[F]): F[ListingResponse[T]] = {
    import fs2.text._
    r.body.through(utf8Decode).compile.to(List).flatMap { l =>
      import io.circe.parser._
      decode[ListingResponse[T]](l.mkString) match {
        case Right(s) => S.pure(s)
        case Left(e) => S.raiseError[ListingResponse[T]](e)
      }
    }
  }

  /**
   * A listing variant with no field filters.
   */
  def listFull[F[_]](bucket: String,
                     listingFilter: ListFilter = ListFilter()
                    )(implicit G: GCStorage[F], S: Sync[F]): fs2.Stream[F, ListingResponse[ObjectMetadata]] =
    listGeneric[F, ObjectMetadata](bucket, listingFilter)

  /**
   * A listing variant with field filters and a partial variant.
   */
  def listPartial[F[_]](bucket: String, listingFilter: ListFilter = ListFilter(), fieldFilter: Seq[String] = Seq.empty)(
    implicit G: GCStorage[F],
    S: Sync[F]
  ): fs2.Stream[F, ListingResponse[PartialObjectMetadata]] =
    listGeneric[F, PartialObjectMetadata](bucket, listingFilter, fieldFilter)

  /**
   * A listing function which takes a circe decoder and applies it, this encoder should correspond to fieldFilter.
   */
  def listDec[F[_], T: Decoder](
                                 bucket: String,
                                 listingFilter: ListFilter = ListFilter(),
                                 fieldFilter: Seq[String] = Seq.empty
                               )(implicit G: GCStorage[F], S: Sync[F]): fs2.Stream[F, ListingResponse[T]] =
    listGeneric[F, T](bucket, listingFilter, fieldFilter)

  /**
   * Like the decoder flavor of [[listDec]] but returns a map which can be used to find the desired value.
   */
  def listAnon[F[_]](bucket: String, listingFilter: ListFilter = ListFilter(), fieldFilter: Seq[String] = Seq.empty)(
    implicit G: GCStorage[F],
    S: Sync[F]
  ): fs2.Stream[F, ListingResponse[Map[String, Json]]] =
    listGeneric[F, Map[String, Json]](bucket, listingFilter, fieldFilter)

  private def listGeneric[F[_], T: Decoder](
                                             bucket: String,
                                             listingFilter: ListFilter,
                                             fieldFilter: Seq[String] = Seq.empty
                                           )(implicit G: GCStorage[F], S: Sync[F]): fs2.Stream[F, ListingResponse[T]] = {
    val (initialUri, initialMethod) =
      ObjectsEndpoints.list(bucket, listingFilter, None, fieldFilter: _*)

    val initial: F[ListingResponse[T]] =
      G.authedRequest(initialMethod, initialUri, EmptyBody)(r => listingBodyHandler[F, T](r))
    // Map over page
    val pages: F[fs2.Stream[F, ListingResponse[T]]] = initial.map { lr =>
      fs2.Stream
        .iterateEval(lr) { prev =>
          prev.nextPageToken match {
            case Some(nextPage) => {
              val (nextUri, nextMethod) = ObjectsEndpoints.list(bucket, listingFilter, Some(nextPage), fieldFilter: _*)

              G.authedRequest(nextMethod, nextUri, EmptyBody)(r => listingBodyHandler[F, T](r))
            }
            case None =>
              S.raiseError[ListingResponse[T]](new Exception("Did not terminate when there was no next page"))
          }
        }
        .takeWhile(_.nextPageToken.isDefined)
    }

    fs2.Stream
      .eval(pages)
      .flatMap(x => x)
  }

  /**
   * Patches the object metadata.
   */
  def patchJson[F[_]](item: GCSItem, newMetadata: Json)(implicit G: GCStorage[F], S: Sync[F]): F[Unit] = {
    val (uri, m) = ObjectsEndpoints.patch(item)

    import fs2.text._

    G.authedRequest(m, uri, fs2.Stream(newMetadata.noSpaces).lift[F].through(utf8Encode))(_ => S.unit)
  }

  /**
   * Patches the object metadata using a structured parameter.
   */
  def patchPartial[F[_]](
                          item: GCSItem,
                          newMetadata: PartialObjectMetadata
                        )(implicit G: GCStorage[F], S: Sync[F]): F[Unit] = {
    import io.circe.syntax._

    val jo: JsonObject = newMetadata.asJsonObject.toMap.filterNot {
      case (_, v) => v.isNull
    }.asJsonObject

    patchJson(item, jo.asJson)
  }

  private def rewriteBodyHandler[F[_]](r: Response[F])(implicit S: Sync[F]): F[Rewrite] = {
    import fs2.text._
    r.body.through(utf8Decode).compile.to(List).flatMap { l =>
      import io.circe.parser._
      decode[Rewrite](l.mkString) match {
        case Left(e) => S.raiseError[Rewrite](e)
        case Right(s) => S.pure(s)
      }
    }
  }

  /**
   * Rewrites a source object to a destination object, optionally with some new metadata.
   *
   * @returns A stream which evaluates all the rewrite steps.
   * [[https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite]]
   */
  def rewrite[F[_]](source: GCSItem, target: GCSItem, newMetadata: PartialObjectMetadata = PartialObjectMetadata())(
    implicit G: GCStorage[F],
    S: Sync[F]
  ): fs2.Stream[F, Rewrite] = {
    import io.circe.syntax._

    val jo: JsonObject = newMetadata.asJsonObject.toMap.filterNot {
      case (_, v) => v.isNull
    }.asJsonObject

    import fs2.text._
    val metadataStream =
      fs2.Stream(jo.asJson.noSpaces).lift[F].through(utf8Encode)

    val (uri, m) = ObjectsEndpoints.rewrite(source, target, None)

    val initial: F[Rewrite] =
      G.authedRequest(m, uri, metadataStream)(r => rewriteBodyHandler(r))

    val its: F[fs2.Stream[F, Rewrite]] = initial.map { r =>
      fs2.Stream
        .iterateEval(r) { prev =>
          prev.rewriteToken match {
            case Some(nextToken) => {
              val (nextUri, nextM) =
                ObjectsEndpoints.rewrite(source, target, Some(nextToken))

              G.authedRequest(nextM, nextUri, metadataStream)(r => rewriteBodyHandler(r))
            }
            case None =>
              S.raiseError[Rewrite](new Exception("Did not terminate on empty rewrite token"))
          }
        }
        .takeWhile(_.rewriteToken.isDefined)
    }

    fs2.Stream
      .eval(its)
      .flatMap(x => x)
  }

  /**
   * Returns a boolean for which true means the object exists, and false meaning it does not.
   * It queries [[getObjectMetadata]], 404 will mean false and 200 will mean true.
   */
  def exists[F[_]](item: GCSItem)(implicit G: GCStorage[F], S: Sync[F]): F[Boolean] =
    G runReader metadataReq[F](item) flatMap (G authedRequest existsHandler[F])

  protected[tray] def existsHandler[F[_]](r: Response[F])(implicit S: Sync[F]): F[Boolean] =
    if (!r.status.isSuccess && r.status != Status.NotFound) GCStorage.raiseResponse(r)
    else S.pure(r.status != Status.NotFound)

  /**
   * Does a single http update call to the GCS endpoint with the supplied metadata changes.
   */
  def update[F[_]](item: GCSItem, updateMetadata: UpdateMetadata)(implicit G: GCStorage[F], S: Sync[F]): F[Unit] = {
    val (uri, m) = ObjectsEndpoints.update(item)

    import fs2.text._
    import io.circe.syntax._

    val nullRemoved: String = updateMetadata.asJsonObject.toMap
      .filterNot { case (_, v) => v.isNull }
      .asJson
      .noSpaces

    val encoded = fs2.Stream(nullRemoved).lift[F].through(utf8Encode)

    G.authedRequest(m, uri, encoded)(_ => S.unit)
  }

  /**
   * Registers a webhook for an object.
   * [[https://cloud.google.com/storage/docs/json_api/v1/objects/watchAll]]
   */
  def watchAll[F[_]](item: GCSItem, watchAll: WatchAll)(implicit G: GCStorage[F], S: Sync[F]): F[Unit] = {
    val (uri, m) = ObjectsEndpoints.update(item)

    import fs2.text._
    import io.circe.syntax._

    val dataStream =
      fs2.Stream(watchAll.asJson.noSpaces).lift[F].through(utf8Encode)

    G.authedRequest(m, uri, dataStream)(_ => S.unit)
  }

  /* Gets the metadata for an object
   * [[https://cloud.google.com/storage/docs/json_api/v1/objects/get]]
   * */
  def metadataReq[F[_]](item: GCSItem): Prepared[F] = {
    val (uri, m) = ObjectsEndpoints.getAlt(item, "json")
    GCStorage.makeRequest[F](m, uri, EmptyBody)
  }

  protected[tray] def metadataHandler[F[_]](res: Response[F])(implicit S: Sync[F]): F[PartialObjectMetadata] =  {
    import fs2.text._
    res.body.through(utf8Decode).compile.to(List).flatMap{ s => 
      import io.circe.parser._
      decode[PartialObjectMetadata](s.mkString) match {
        case Left(e) => S.raiseError[PartialObjectMetadata](e)
        case Right(s) => S.pure(s)
      }
    }
  }

  def metadata[F[_]: Sync](item: GCSItem)(implicit G: GCStorage[F]): F[PartialObjectMetadata] =
    G runReader metadataReq[F](item) flatMap (G authedRequest (x => metadataHandler(x)))
}
