package tray

import cats.effect.{ExitCode, IO, IOApp, Resource}
import tray.api.GCSStorage
import tray.auth.TokenDispenser

object Main extends IOApp {

  val td = TokenDispenser[IO]

  val storage: Resource[IO, GCSStorage[IO]] = GCSStorage[IO](td.unsafeRunSync())

  override def run(args: List[String]): IO[ExitCode] = {
    val a: IO[Unit] = storage
      .use{ storage =>
        storage
          .getObject(GCSItem("os-valdemargr", "test"))
          .map(ab => println(new String(ab)))
      }

    a.as(ExitCode.Success)
  }
}
