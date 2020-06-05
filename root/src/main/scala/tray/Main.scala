package tray

import java.nio.file.Paths

import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
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

        val a = IO {
          println(s"a Uploading ${(256 * 1000 + 42).toString} kb chunked, with 30 chunk factor")
          for {
            start <- IO(System.nanoTime())
            _ <- storage
              .putObjectChunked(GCSItem("os-valdemar", "big-guy5"), fileData, 30)
            end <- IO(System.nanoTime())
          } yield println(s"Took ${(end - start).toString} time")
        }.flatMap(x => x)

        val b = IO {
          println(s"b Uploading ${(256 * 1000 + 42).toString} kb normally")
          for {
            start <- IO(System.nanoTime())
            _ <- storage
              .putObject(GCSItem("os-valdemar", "big-test"), fileData)
            end <- IO(System.nanoTime())
          } yield println(s"Took ${(end - start).toString} time")
        }.flatMap(x => x)

        val c = IO {
          println(s"c Uploading ${(256 * 1000 + 42).toString} kb parallel, with 30 chunk factor and 16 threads")
          for {
            start <- IO(System.nanoTime())
            _ <- storage
              .putParallel(GCSItem("os-valdemar", "par"), fileData, 16, 30, "tmp")
            end <- IO(System.nanoTime())
          } yield println(s"Took ${(end - start).toString} time")
        }.flatMap(x => x)

        for {
          _ <- c.handleErrorWith(_ => IO(println("c failed")))
          _ <- a.handleErrorWith(_ => IO(println("a failed")))
          _ <- b.handleErrorWith(_ => IO(println("b failed")))
        } yield ExitCode.Success
      }
    }
}
