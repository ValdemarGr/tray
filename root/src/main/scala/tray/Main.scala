package tray

import cats.{Eval, Monad}
import cats.data.{IndexedStateT, State}
import cats.effect.{ExitCode, IO, IOApp}
import cats.free.Free
import tray.underlying.FreeRunner

object Main extends IOApp {

  //val td: IO[TokenDispenser[IO]] = TokenDispenser[IO]

  //val storage: Resource[IO, GCStorage[IO]] = GCStorage[IO](td.unsafeRunSync())

  override def run(args: List[String]): IO[ExitCode] = {
    /*storage
      .use { implicit storage =>
        println(Objects.getObjectChunked(GCSItem("os-valdemar", "myfile"), chunkFactor=10, onFailure = (_ => IO.pure(println("Failure!"))))
          .compile
          .toList
          .unsafeRunSync()
          .flatMap(_.toList)
          .length)

        IO(ExitCode.Success)
      }*/

    val fr = new FreeRunner()

    val p: Free[fr.BatchedF, Unit] = for {
      _ <- fr.get("a")
      _ <- fr.get("b")
      _ <- fr.get("c")
    } yield ()


    val o = p.foldMap(fr.compiler)()
    IO(ExitCode.Success)
  }
}
