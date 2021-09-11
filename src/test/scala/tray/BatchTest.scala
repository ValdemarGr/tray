package tray

import munit.CatsEffectSuite
import org.http4s.Request
import org.http4s.Response
import cats._
import java.util.UUID
import cats.implicits._
import cats.effect._

final case class Metadata(size: Long)

trait Storage[F[_]] {
  def doSomeAction: Batch[F, Metadata]
}

trait Batch[F[_], A] {
  val requests: List[Request[F]]
  def extract(response: Response[F]): F[A]
}

trait BatchRunner[F[_]] {
  def toMultipart(mr: List[Request[F]]): Request[F] = ???

  def httpCall(request: Request[F]): F[Response[F]] = ???

  def run[A](batch: Batch[F, A])(implicit M: Monad[F]): F[A] =
    httpCall(toMultipart(batch.requests)).flatMap { res =>
      batch.extract(res)
    }
}

object Batch {
  def toBatch[F[_], A](fa: F[A]): Batch[F, A] =
    new Batch[F, A] {
      override val requests: List[Request[F]] = List.empty

      override def extract(response: Response[F]): F[A] = fa
    }

  implicit def applicativeForBatch[F[_]: Applicative]: Applicative[Batch[F, *]] =
    new Applicative[Batch[F, *]] {
      override def ap[A, B](ff: Batch[F, A => B])(fa: Batch[F, A]): Batch[F, B] =
        new Batch[F, B] {
          val requests: List[Request[F]] = ff.requests ++ fa.requests
          def extract(response: Response[F]): F[B] =
            ff.extract(response).ap(fa.extract(response))
        }
      override def pure[A](x: A): Batch[F, A] =
        new Batch[F, A] {
          val requests = List.empty
          def extract(response: Response[F]): F[A] =
            Applicative[F].pure(x)
        }
    }

  def monadForBatchRunner[F[_]: Monad](br: BatchRunner[F]): Monad[Batch[F, *]] =
    new Monad[Batch[F, *]] {
      override def flatMap[A, B](fa: Batch[F, A])(f: A => Batch[F, B]): Batch[F, B] =
        new Batch[F, B] {
          override val requests: List[Request[F]] = fa.requests
          override def extract(response: Response[F]): F[B] =
            br.run(fa).flatMap(a => br.run(f(a)))
        }

      override def tailRecM[A, B](a: A)(f: A => Batch[F, Either[A, B]]): Batch[F, B] =
        toBatch(Monad[F].tailRecM(a)(a => br.run(f(a))))

      override def pure[A](x: A): Batch[F, A] = Applicative[Batch[F, *]].pure(x)
    }

  val storage: Storage[IO] = ???
  val b: (Batch[IO, Metadata], Batch[IO, Metadata], Batch[IO, Metadata]) =
    (storage.doSomeAction, storage.doSomeAction, storage.doSomeAction)
}

class BatchTest extends CatsEffectSuite {}
