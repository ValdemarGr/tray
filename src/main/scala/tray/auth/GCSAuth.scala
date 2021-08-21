package tray.auth

import com.google.auth.oauth2.GoogleCredentials
import cats.effect.Sync
import com.google.auth.oauth2.AccessToken
import org.http4s._
import org.http4s.headers._

trait GCSAuth[F[_]] {
  val getToken: F[AccessToken] 

  val getHeader: F[Headers] 
}

object GCSAuth {
  def apply[F[_]: Sync](credentials: GoogleCredentials): GCSAuth[F] = new GCSAuth[F] {
    // We do not trust google
    import cats.implicits._
    val getToken: F[AccessToken] =
      for {
        _ <- Sync[F].blocking(credentials.refreshIfExpired())
        token <- Sync[F].blocking(credentials.getAccessToken())
      } yield token

    val getHeader: F[Headers] =
      getToken
        .map(token => Credentials.Token(AuthScheme.Bearer, token.getTokenValue()))
        .map(token => Headers(Authorization(token)))
  }

  import cats.implicits._
  def apply[F[_]: Sync]: F[GCSAuth[F]] =
    Sync[F]
      .blocking(GoogleCredentials.getApplicationDefault())
      .map(credentials => GCSAuth[F](credentials))
}
