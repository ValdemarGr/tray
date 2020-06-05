package tray

import java.nio.file.Paths

import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import com.google.cloud.storage.{BlobId, BlobInfo, StorageOptions}
import tray.api.GCSStorage
import tray.auth.TokenDispenser

object Main extends IOApp {

  val td = TokenDispenser[IO]

  val storage: Resource[IO, GCSStorage[IO]] = GCSStorage[IO](td.unsafeRunSync())

  override def run(args: List[String]): IO[ExitCode] = {
    storage
      .use { storage =>
        val fileData: fs2.Stream[IO, Byte] = fs2.io.file.readAll[IO](Paths.get("/tmp/myfile"), Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.global), 50000)

        /*storage
          .getObject(GCSItem("os-valdemargr", "tmp.py"))
          .map(ab => println(new String(ab)))

*/

        val s = StorageOptions.getDefaultInstance.getService

        val d = IO{
          println(s"d [OFFICIAL SDK] Uploading 20mb normally")
          for {
            start <- IO(System.nanoTime())
            fd <- fileData.compile.to(Array)
            _ <- IO{
              s.create(BlobInfo.newBuilder(BlobId.of("os-valdemar", "default-normal")).build(), fd)
            }
            end <- IO(System.nanoTime())
          } yield println(s"Took ${(end - start).toString} time")
        }.flatMap(x => x)

        val e = IO{
          println(s"e [OFFICIAL SDK] Uploading 20mb with 1 chunk factor")
          for {
            start <- IO(System.nanoTime())
            w = s.writer(BlobInfo.newBuilder(BlobId.of("os-valdemar", "default-normal")).build())
            _ <- fileData.chunkN(1 * 256 * 1024)
                  .map(_.toByteBuffer)
                  .map(w.write)
                  .compile
                  .drain
            end <- IO(System.nanoTime())
          } yield println(s"Took ${(end - start).toString} time")
        }.flatMap(x => x)

        val a = IO {
          println(s"a [TRAY] Uploading 20mb chunked, with 1 chunk factor")
          for {
            start <- IO(System.nanoTime())
            _ <- storage
              .putObjectChunked(GCSItem("os-valdemar", "big-guy5"), fileData, 1)
            end <- IO(System.nanoTime())
          } yield println(s"Took ${(end - start).toString} time")
        }.flatMap(x => x)

        val b = IO {
          println(s"b [TRAY] Uploading 20mb normally")
          for {
            start <- IO(System.nanoTime())
            _ <- storage
              .putObject(GCSItem("os-valdemar", "big-test"), fileData)
            end <- IO(System.nanoTime())
          } yield println(s"Took ${(end - start).toString} time")
        }.flatMap(x => x)

        val c = IO {
          println(s"c [TRAY] Uploading 20mb parallel, with 1 chunk factor and 16 threads")
          for {
            start <- IO(System.nanoTime())
            _ <- storage
              .putParallel(GCSItem("os-valdemar", "par"), fileData, 16, 1, "tmp")
            end <- IO(System.nanoTime())
          } yield println(s"Took ${(end - start).toString} time")
        }.flatMap(x => x)

        for {
          _ <- d.handleErrorWith(e => IO(println(s"d failed with ${e.getMessage}")))
          _ <- e.handleErrorWith(e => IO(println(s"e failed with ${e.getMessage}")))
          _ <- c.handleErrorWith(e => IO(println(s"c failed with ${e.getMessage}")))
          _ <- a.handleErrorWith(e => IO(println(s"a failed with ${e.getMessage}")))
          _ <- b.handleErrorWith(e => IO(println(s"b failed with ${e.getMessage}")))
        } yield ExitCode.Success
      }
    }
}
