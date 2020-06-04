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

        import fs2.text._

        val string: fs2.Stream[IO, Byte] = fs2.Stream("a", "b", "c")
          .evalMap(c => IO.pure(c))
          .repeatN(256 * 1024 * 2)
          .through(utf8Encode)

        storage
          .uploadChunked(GCSItem("os-valdemargr", "big-guy6"), string, 1)
      }.as(ExitCode.Success)
  }
}
