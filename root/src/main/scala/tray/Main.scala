package tray

import cats.effect.{ExitCode, IO, IOApp, Resource}
import tray.api.GCSStorage
import tray.auth.TokenDispenser

object Main extends IOApp {

  val td = TokenDispenser[IO]

  val storage: Resource[IO, GCSStorage[IO]] = GCSStorage[IO](td.unsafeRunSync())

  override def run(args: List[String]): IO[ExitCode] = {
    storage
      .use{ storage =>
        /*storage
          .getObject(GCSItem("os-valdemargr", "tmp.py"))
          .map(ab => println(new String(ab)))*/

        val a = storage.getB(GCSItem("os-valdemargr", "test"))

        val ab: storage.UnfinishedBatch = a

        storage
          .batched(IO.pure(ab))
          .map(ab => println(new String(ab)))
      }.as(ExitCode.Success)
  }
}
