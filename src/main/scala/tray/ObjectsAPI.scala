package tray

import fs2._
import org.http4s.headers._
import cats.effect.kernel.Resource
import org.http4s.Uri

trait ObjectsAPI[F[_]] {
  def putBlob(path: StoragePath, bytes: Array[Byte]): F[Unit]

  def putPipe(path: StoragePath, length: Long): Pipe[F, Byte, Unit]

  def putResumableFrom(uri: Uri, offset: Long, chunkFactor: Int = 1): Pipe[F, Byte, (Throwable, Long)]

  def putResumable(path: StoragePath, chunkFactor: Int = 1): Pipe[F, Byte, (Throwable, Location, Long)]

  def getBlob(path: StoragePath): Stream[F, Byte]

  def getRange(path: StoragePath, start: Long, end: Long, chunkFactor: Int = 1): Stream[F, Byte]

  def getResumableFrom(path: StoragePath, start: Long, chunkFactor: Int = 1): Stream[F, Byte]

  def getResumable(path: StoragePath, chunkFactor: Int = 1): Stream[F, Byte]

  val byteStreamToResumable: Pipe[F, Byte, Either[(Throwable, Long), Chunk[Byte]]]

  def getMetadata(path: StoragePath): F[BlobMetadata]
}
