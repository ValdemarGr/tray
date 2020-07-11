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
