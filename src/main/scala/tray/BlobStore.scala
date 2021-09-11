package tray

import tray.auth.GCSAuth
import org.http4s.client.Client
import org.http4s.Request
import org.http4s.Method
import tray.endpoints.Endpoints
import org.http4s.Response
import cats.effect.kernel.Concurrent
import tray.errors.TrayError
import fs2._

import cats.implicits._
import org.http4s._
import org.http4s.headers._
import cats.effect.kernel.Resource
import cats.data.NonEmptyList
import cats.data.EitherT

final case class BlobStore[F[_]](
  auth: GCSAuth[F],
  client: Client[F]
)
