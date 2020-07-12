package tray.underlying

import cats.{Applicative, ContravariantMonoidal, Eval, FlatMap, Functor, Monad}
import cats.data.State

object Test {
  type BatchFragmentId = String

  trait FragmentRequest

  trait FragmentResponse

  type S = Map[BatchFragmentId, FragmentRequest]

  object Batch {
    def make[A](s: S, f: Map[BatchFragmentId, FragmentResponse] => A): Batch[A] = Batch[A](
      transformer = f,
      state = s
    )
  }

  implicit object BatchApplicative extends Applicative[Batch] {
    override def pure[A](x: A): Batch[A] = Batch[A](
      transformer = _ => x,
      state = Map.empty
    )

    override def ap[A, B](ff: Batch[A => B])(fa: Batch[A]): Batch[B] = {
      val f: Map[String, FragmentResponse] => B = (r: Map[BatchFragmentId, FragmentResponse]) => ff.transformer(r)(fa.transformer(r))
      Batch[B](
        transformer = f,
        state = ff.state ++ fa.state
      )
    }
  }

  case class Batch[A](
                       transformer: Map[BatchFragmentId, FragmentResponse] => A,
                       state: S
                     )

  implicit class BatchOps[A](fa: Batch[A])(implicit A: Applicative[Batch]) {
    def |>[B](fb: Batch[B]): Batch[(A, B)] = A.product(fa, fb)
  }

  // Example
  case class SomeFragmentRequest(x: Int) extends FragmentRequest

  case class SomeFragmentResponse(x: Int) extends FragmentResponse

  val id: BatchFragmentId = "some-id"
  val exampleBatch = Batch.make[SomeFragmentResponse](
    s = Map(id -> SomeFragmentRequest(42)),
    f = responses => responses.get(id).map { case x: SomeFragmentResponse => x }.get
  )

  // Example of composition
  val o: Batch[(SomeFragmentResponse, SomeFragmentResponse)] = exampleBatch |> exampleBatch
  // N arity
  val o2: Batch[(SomeFragmentResponse, SomeFragmentResponse, SomeFragmentResponse, SomeFragmentResponse)] =
    Applicative[Batch].tuple4(exampleBatch, exampleBatch, exampleBatch, exampleBatch)

  def run(s: S): Map[BatchFragmentId, FragmentResponse] = ???

  def resolve[R](r: Map[BatchFragmentId, FragmentResponse], b: Batch[R]): R = b.transformer(r)

  val res: (SomeFragmentResponse, SomeFragmentResponse) = resolve(run(o.state), o)
}