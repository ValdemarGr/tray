package tray

import fs2._
import org.http4s.headers._
import cats.effect.kernel.Resource

trait ObjectsAPI[F[_]] {
  def putBlob(path: StoragePath, bytes: Array[Byte]): F[Unit]

  //triggers chunked transfer encoding
  def putPipe(path: StoragePath, length: Long): Pipe[F, Byte, Unit]

  def putPipeChunked(path: StoragePath): Pipe[F, Byte, Unit]

  //enables resumed uploads
  def putResumable(path: StoragePath, chunkFactor: Int = 1): Pipe[F, Byte, (Throwable, Location, Long)]

  def getBlob(path: StoragePath): Resource[F, Stream[F, Byte]]
}
