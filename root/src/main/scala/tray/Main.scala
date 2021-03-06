package tray

import tray.api.{GCStorage, Objects}
import tray.auth.TokenDispenser
import tray.core.GCSItem
import tray.serde.Compose
import tray.serde.Compose.{ComposeDestination, ComposeItem}

object Main extends IOApp {

  val td: IO[TokenDispenser[IO]] = TokenDispenser[IO]

  val storage: Resource[IO, GCStorage[IO]] = GCStorage[IO](td.unsafeRunSync())

  override def run(args: List[String]): IO[ExitCode] =
    storage
      .use { implicit s =>
        //import fs2.text._
        //val data = fs2.Stream("a", "b", "c").repeat.take(1024 * 1024 * 4).through(utf8Encode)
        for {
          //_ <- Objects.putParallel(GCSItem("os-valdemar", "testeheste"), data.lift[IO], 4, 1, "temporary")
          _ <- Objects.compose[IO](
            GCSItem("os-valdemar", "test-out"),
            Compose(sourceObjects = List(1,2,3,4,5).map(i => (ComposeItem(s"temporary-${i}"))),
                    destination = ComposeDestination("application/json"))
          )
          o <- IO.pure(ExitCode.Success)
        } yield o
      }
}
