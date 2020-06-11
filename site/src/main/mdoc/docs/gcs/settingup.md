---
layout: docs
title: "Setting up"
---
# {{page.title}}
Before doing anything we must be able to authenticate with the google api.
To do this we must be able to generate oauth2 tokens, which can be a challenge in itself.

Fortunately the official google java sdk comes with an authentication module, which we will use.
The underlying token module issues requests with synchronous google http library, which is okay since it is initial and the expiration time is quite large.

## Tokens
To begin we must instantiate the `TokenDispenser[F[_]]` class, which optionally takes a `com.google.auth.oauth2.GoogleCredentials` object.
In the case of no supplied credentials, it will fall back to the default behaviour of [google credentials searching](https://github.com/googleapis/google-cloud-java#authentication).
```scala
val td: IO[TokenDispenser[IO]] = TokenDispenser[IO]
```
Or
```scala
val creds: com.google.auth.oauth2.GoogleCredentials = ???

val td: TokenDispenser[IO] = TokenDispenser[IO](creds)
```
Keep in mind that the getting credentials from something like a file is a side effect.

Furthermore to issue a new token, we can query the google api, but the underlying implementation will handle this automatically.
```scala
val token: IO[AccessToken] = td.flatMap(_.getToken)
```

## Storage interface
The underlying http implementation is based on http4s with [async-http-client](https://github.com/AsyncHttpClient/async-http-client) as the backend, but any backend can work as the `Client` interface from http4s is used.
```scala
val td: TokenDispenser[IO] = ???

val storage: Resource[IO, GCStorage[IO]] = GCStorage[IO](td)
```
Or
```scala
val client: Client[IO] = ???
val storage = GCStorage[IO](client, td)
```

### Uploading
Uploading can be done in a different number of ways.
* A single http request with the entire payload, which can fail.
* A chunked http request which is resumable and safe, but slower.
* A parallel upload which uploads individual chunks to temporary objects, then "composes" using Google Cloud's compose function, which should only be used for larger files.