package tray.underlying

import cats.Applicative
import org.http4s.{Request, Response}

protected[tray] case class Batch[A, F[_]](
                                           protected[tray] val transformer: Batch.R[F, A],
                                           protected[tray] val state: Batch.S[F]
                                         )

object Batch {
  type BatchFragmentId = String
  type S[F[_]] = Map[BatchFragmentId, Request[F]]
  type RS[F[_]] = Map[BatchFragmentId, Response[F]]
  type R[F[_], A] = RS[F] => A

  protected[tray] def unitR[F[_], A]: RS[F] => Unit = _ => ()

  protected[tray] def make[F[_], A](s: S[F], f: R[F, A]): Batch[A, F] = Batch[A, F](
    transformer = f,
    state = s
  )

  protected[tray] def batchInstance[F[_]]: Applicative[Batch[*, F]] = new Applicative[Batch[*, F]] {
    override def pure[A](x: A): Batch[A, F] = Batch[A, F](
      transformer = _ => x,
      state = Map.empty
    )

    override def ap[A, B](ff: Batch[A => B, F])(fa: Batch[A, F]): Batch[B, F] = {
      val f: RS[F] => B = (r: RS[F]) => ff.transformer(r)(fa.transformer(r))
      Batch[B, F](
        transformer = f,
        state = ff.state ++ fa.state
      )
    }
  }

  implicit class BatchOps[A, F[_]](fa: Batch[A, F])(implicit A: Applicative[Batch[*, F]]) {
    def <>[B](fb: Batch[B, F]): Batch[(A, B), F] = A.product(fa, fb)
  }
}