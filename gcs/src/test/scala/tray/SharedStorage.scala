package tray

import cats.effect.{ContextShift, IO, Timer}
import tray.api.GCStorage
import tray.auth.TokenDispenser

import scala.concurrent.ExecutionContext

object SharedStorage {
  private val ec: ExecutionContext = ExecutionContext.Implicits.global
  private implicit val timer: Timer[IO] = IO.timer(ec)
  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)

  val td: IO[TokenDispenser[IO]] = TokenDispenser[IO]
  implicit val (storage: GCStorage[IO], _) =
    GCStorage[IO](td.unsafeRunSync()).allocated.unsafeRunSync()
}
