package tray.api

import java.util.UUID

import cats.Id
import cats.effect._
import fs2.Chunk
import org.http4s._
import tray.api.GCStorage.Prepared
import tray.batch.Batch

object RequestUtil {
  import cats.implicits._

  protected[tray] def beginOffsetRange(begin: Long, endAt: Long, stepSize: Long): fs2.Stream[Id, (Long, Long)] = fs2.Stream.iterate((begin, math.min(begin + stepSize, endAt - 1))){ case (_, prevEnd) =>
    val offset = prevEnd + 1
    val start = offset
    val end = math.min(offset + stepSize, endAt - 1)
    (start, end)
  }


  protected[tray] def effectfulBatch[F[_] : Sync](p: Prepared[F])(implicit G: GCStorage[F]): F[Batch[Unit, F]] =
    G.runReader (p) map (r => Batch.make(Map(UUID.randomUUID().toString -> r), Batch.unitR))

  protected[tray] def effectfulReq[F[_] : Sync](p: Prepared[F])(implicit G: GCStorage[F]): F[Unit] =
    effectfulReqAllowErr(Set.empty)(p)

  protected[tray] def effectfulReqAllowErr[F[_]](sc: Set[Status])(p: Prepared[F])(implicit G: GCStorage[F], S: Sync[F]): F[Unit] =
    G.runReader (p) flatMap (G authedRequest GCStorage.raiseOnBadStatus(sc) (_ => S.unit))

  protected [tray] trait AbstractType
  protected [tray] case object AbstractDone extends AbstractType
  protected [tray] case class AbstractNotDone(endAt: Long) extends AbstractType

  protected [tray] trait Offset
  protected [tray] trait OffsetWithBody extends Offset
  protected [tray] trait OffsetWithoutBody extends Offset

  protected [tray] case class DoneWithBody(body: Chunk[Byte]) extends OffsetWithBody
  protected [tray] case class NotDoneWithBody(offset: Long, body: Chunk[Byte]) extends OffsetWithBody

  protected [tray] case object Done extends OffsetWithoutBody
  protected [tray] case class NotDone(offset: Long) extends OffsetWithoutBody

  protected [tray] case class FailedAt(offset: Long) extends OffsetWithoutBody with OffsetWithBody

  protected [tray] trait OffsetConstructor[T <: Offset] {
    def construct[F[_]](p: AbstractType, r: Response[F])(implicit S: Sync[F]): F[T]
    def fail[F[_]](fa: FailedAt)(implicit S: Sync[F]): F[T]
  }

  protected [tray] implicit object OffsetWithBodyConstructor extends OffsetConstructor[OffsetWithBody] {
    override def construct[F[_]](p: AbstractType, r: Response[F])(implicit S: Sync[F]): F[OffsetWithBody] = p match {
      case AbstractDone => r.body.compile.to(Chunk).map(c => DoneWithBody(c))
      case AbstractNotDone(endAt) => r.body.compile.to(Chunk).map(c => NotDoneWithBody(endAt, c))
    }
    override def fail[F[_]](fa: FailedAt)(implicit S: Sync[F]): F[OffsetWithBody] = S.pure(fa)
  }

  protected [tray] implicit object OffsetWithoutConstructor extends OffsetConstructor[OffsetWithoutBody] {
    override def construct[F[_]](p: AbstractType, r: Response[F])(implicit S: Sync[F]): F[OffsetWithoutBody] = p match {
      case AbstractDone => S.pure(Done)
      case AbstractNotDone(endAt) => S.pure(NotDone(endAt))
    }
    override def fail[F[_]](fa: FailedAt)(implicit S: Sync[F]): F[OffsetWithoutBody] = S.pure(fa)
  }

  protected [tray] def doBackoffRangedRequest[R <: Offset, F[_]: Timer](m: Method, uri: Uri, body: EntityBody[F], previousBegin: Long, h: Header*)
                                                              (implicit G: GCStorage[F], S: Sync[F], O: OffsetConstructor[R]): F[R] = {
    import scala.concurrent.duration._

    val failF = S.raiseError[R](new Exception("some api error occured"))

    // Do backoff
    fs2.Stream.retry(
      fo = G.authedRequest(m, uri, body, h: _*) { r =>
        val secondRangeHeader: Option[Long] = for {
          h <- r.headers.get(org.http4s.headers.Range)
          end <- h.ranges.head.second
        } yield end

        val secondContentRangeHeader: Option[Long] = for {
          h <- r.headers.get(org.http4s.headers.`Content-Range`)
          end <- h.range.second
        } yield end

        val combined = secondRangeHeader orElse secondContentRangeHeader

        (r.status, combined) match {
          case (status, Some(endRange)) if status.code == Status.PartialContent.code =>
            O.construct(AbstractNotDone(endRange + 1), r)
          case (status, _) if status.responseClass == Status.Successful =>
            O.construct(AbstractDone, r)
          case (status, _) if status.code != 308 =>
            failF
          case (_, Some(endRange)) =>
            O.construct(AbstractNotDone(endRange + 1), r)
          case (_, None) =>
            failF
        }
      },
      delay = 2.seconds,
      nextDelay = last => (last.toSeconds^2).seconds,
      maxAttempts = 4
    )
      .handleErrorWith(_ => fs2.Stream.eval(O.fail(FailedAt(previousBegin))))
      .compile
      .lastOrError
  }
}
