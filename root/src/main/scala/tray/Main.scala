package tray

import cats.effect.{ExitCode, IO, IOApp, Resource}
import tray.api.{GCStorage, Objects}
import tray.auth.TokenDispenser

object Main extends IOApp {

  val td: IO[TokenDispenser[IO]] = TokenDispenser[IO]

  val storage: Resource[IO, GCStorage[IO]] = GCStorage[IO](td.unsafeRunSync())

  override def run(args: List[String]): IO[ExitCode] = {
    storage
      .use { implicit storage =>
        //val fileData: fs2.Stream[IO, Byte] = fs2.io.file.readAll[IO](Paths.get("/tmp/myfile"), Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.global), 50000)

        //storage.putParallel(GCSItem("os-valdemar", "testitem"), fileData, 16, 8, "tmppp").as(ExitCode.Success)

        val range = 5

        fs2.Stream.range(0, 20, range).compile.toList.foreach(x => println(s"${x.toString} - ${(x + range - 1).toString}"))

        println(Objects.getObjectChunked(GCSItem("os-valdemar", "myfile"), chunkFactor=10, onFailure = (_ => IO.pure(println("Failure!"))))
          .compile
          .toList
          .unsafeRunSync()
          .flatMap(_.toList)
          .length)

        IO(ExitCode.Success)
      }
    }
}
