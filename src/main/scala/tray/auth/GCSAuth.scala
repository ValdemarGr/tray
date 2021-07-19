package tray.auth

import com.google.auth.oauth2.GoogleCredentials
import cats.effect.Sync
import com.google.auth.oauth2.AccessToken

abstract class GCSAuth[F[_]: Sync](underlying: GoogleCredentials) {
  // We do not trust google
  import cats.implicits._
  val getToken: F[AccessToken] =
    for {
      _ <- Sync[F].blocking(underlying.refreshIfExpired())
      token <- Sync[F].blocking(underlying.getAccessToken())
    } yield token
}

object GCSAuth {
  def apply[F[_]: Sync](credentials: GoogleCredentials): GCSAuth[F] = new GCSAuth[F](credentials) {}

  import cats.implicits._
  def apply[F[_]: Sync]: F[GCSAuth[F]] =
    Sync[F]
      .blocking(GoogleCredentials.getApplicationDefault())
      .map(credentials => new GCSAuth[F](credentials) {})
}
