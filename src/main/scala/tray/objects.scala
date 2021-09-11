package tray

trait ObjectsAPISyntax {
  import cats.effect._
  implicit def objectsApiSyntax[F[_]: Concurrent](bs: BlobStore[F]): ObjectsAPI[F] = ObjectsAPI[F](bs)
}

object objects extends ObjectsAPISyntax
