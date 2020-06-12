---
layout: docs
title: "Downloading"
---
# {{page.title}}
Downloading or "getting" comes in two different flavor, as opposed to three in uploading.
* The simple get.
* The more complicated resumable, chunked get.

## Simple download
The simple download is very straight forward, it takes a `GCSItem` and returns the corresponding item as `F[Array[Byte]]`.
```scala
val data: F[Array[Byte]] = Objects.getObject(GCSItem("bucket", "path/to/key"))
```
It should be noted that it doesn't make sense to return the item as a `fs2.Stream` since all bytes are already in memory at this time.

## Chunked download
The chunked download is much more complicated than the simple counterpart.
The chunked version ensures safety by using the returned `Content-Length` header, which indicates how many bytes were handled by GCS.
The chunked version also allows for offsetting and ranging the desired subset of the data if necessary.
If it fails mid-download, a callback `onFailure: Long => F[Unit]` is used to ensure that the caller can control if and how they proceed, the `Long` the the offset reached, which should the beginning of the next request.
If say a large `100Gb` file is downloading, but it crashes at `98%`, resuming from this offset at a later point could be desirable.
```scala
val data: fs2.Stream[F, Chunk[Byte]] = Objects.getObjectChunked(GCSItem("bucket", "path/to/key"), chunkFactor=10, onFailure = (_ => IO.pure(println("Failure!"))))
```
Note that the chunk factor is a factor of 256kb, so setting `chunkFactor=4` would result in `1mb` chunks.
Also note that raising an error in `onFailure`, is also an option.
### Parallelism
It should be noted that a parallel version is indeed possible but not implemented as it should be the callers responsibility to figure out what fits best for their use-case in terms of if a single error happens or such.
A simple solution would be the following.
If parallelism is wished for, once should instead create a stream of byte ranges like for instance `[0-99, 100-199, 200-299...]`, then request the objects for each byte range by using something like `fs2.Stream.parEvalMapUnordered`, and finally fold the streams together.
```scala
val chunkSize = 100
val amountOfBytes = 1000

val byteRanges: fs2.Stream[F, fs2.Stream[F, Chunk[Byte]]] = fs2.Stream
  .range(0, amountOfBytes, chunkSize)
  .lift[F]
  .parEvalMapUnordered(maxConcurrent = 6) { offset =>
      val end = offset + chunkSize - 1
    
      Objects.getObjectChunked(
        item=GCSItem("bucket", "path/to/key"), 
        chunkFactor=10, 
        onFailure = (_ => IO.pure(println("Failure!"))), 
        beginAt=offset, 
        endAt=Some(end))
    }
```