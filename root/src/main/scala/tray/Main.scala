package tray

import cats.effect.{ExitCode, IO, IOApp, Resource}
import tray.api.{GCStorage, Objects}
import tray.auth.TokenDispenser

object Main extends IOApp {

  val td: IO[TokenDispenser[IO]] = TokenDispenser[IO]

  val storage: Resource[IO, GCStorage[IO]] = GCStorage[IO](td.unsafeRunSync())

  override def run(args: List[String]): IO[ExitCode] =
    storage
      .use { implicit s =>
      import fs2.text._
      val data = fs2.Stream("a", "b", "c").repeat.take(1024 * 1024 * 4).through(utf8Encode)
        Objects.putParallel(GCSItem("os-valdemar", "testeheste"), data.lift[IO], 4, 1, "temporary") *> IO.pure(ExitCode.Success)
      }
}
