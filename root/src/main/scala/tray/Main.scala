package tray

import cats.{Eval, Monad}
import cats.data.{IndexedStateT, State}
import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.free.Free
import tray.api.GCStorage
import tray.auth.TokenDispenser

object Main extends IOApp {

  val td: IO[TokenDispenser[IO]] = TokenDispenser[IO]

  val storage: Resource[IO, GCStorage[IO]] = GCStorage[IO](td.unsafeRunSync())

  override def run(args: List[String]): IO[ExitCode] = {
    storage
      .use { s =>
        IO(ExitCode.Success)
      }
  }
}
