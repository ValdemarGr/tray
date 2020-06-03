package tray.auth

import cats.effect.Sync
import com.google.auth.oauth2.{AccessToken, GoogleCredentials}

class TokenDispenser[F[_]](creds: GoogleCredentials)(implicit S: Sync[F]) {
    def getToken: F[AccessToken] =
        S.delay{
            creds.refreshIfExpired()
            creds.getAccessToken
        }
}

object TokenDispenser {
    def apply[F[_]: Sync](creds: GoogleCredentials): TokenDispenser[F] = new TokenDispenser[F](creds)

    def apply[F[_]](implicit S: Sync[F]): F[TokenDispenser[F]] =
        S.map(S.delay(GoogleCredentials.getApplicationDefault))(creds => new TokenDispenser(creds))
}