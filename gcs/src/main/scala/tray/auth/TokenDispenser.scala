package tray.auth

import cats.Monad
import cats.effect.IO
import com.google.auth.oauth2.{AccessToken, GoogleCredentials}

class TokenDispenser[F[_]](creds: GoogleCredentials)(implicit F: Monad[F]) {
    def getToken: F[AccessToken] =
        F.pure{
            creds.refreshIfExpired()
            creds.getAccessToken
        }
}

object TokenDispenser {
    def apply[F[_]: Monad](creds: GoogleCredentials): TokenDispenser[F] = new TokenDispenser[F](creds)

    def apply[F[_]: Monad]: IO[TokenDispenser[F]] = for {
        creds <- IO(GoogleCredentials.getApplicationDefault)
    } yield  TokenDispenser(creds)
}