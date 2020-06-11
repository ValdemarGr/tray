---
layout: docs
title: "Uploading"
---
# {{page.title}}
Uploading can be done in a different number of ways.
* A single http request with the entire payload, which can fail.
* A chunked http request which is resumable and safe, but slower.
* A parallel upload which uploads individual chunks to temporary objects, then "composes" using Google Cloud's compose function, which should only be used for larger files.

## Simple uploads
Simple uploads are just as non-blocking as any of the other operations (if you disregard what parallelism brings).
Assuming we have the `GCStorage` implicitly in scope.
```scala
val data: fs2.Stream[F, Byte] = ???

val effect: F[Unit] = Objects.putObject(GCSItem("bucket", "path/to/key"), data)
```

## Chunked resumable
Chunked resumable uploads provide much more safety than simple uploads, in the way that they ensure that the data is delivered via google's own resumable uploads.
The chunked resumable uses `fs2.Stream` and `fs2.Chunk` to partition the data and then sequentially upload each chunk to the same object.
It is crucial that the resumable upload is sequential since the next chunk size is dependent on how many bytes the api _accepted_.
Furthermore it also important to note that Google only accepts sizes in resumable uploads, of multiples of 256kb, the `chunkFactor` parameter is simply such a multiple of 256kb.
Finally a `beginAt` parameter is given, which can be used to resume uploads if an upload does not begin at position 0 (note that it is the caller's responsibility to make sure the data has had some of its first elements removed to account for this `beginAt` offset).
```scala
val effect: OptionT[F, Long] = Objects.putObjectChunked(GCSItem("bucket", "path/to/key"), data, chunkFactor=8, beginAt=0)
```
Note that the `OptionT[F, Long]` is defined if the upload failed, the `Long` being the offset which should be begun at when trying next time.

## Parallel upload
Parallel uploading should theoretically be much faster if the bandwidth is large enough, though it is not a very good choice for a small data size.
Parallel uploading does also not have the benefits that chunked resumable has which is safety via resumability.

The parallel uploading method uses a chunking factor like the resumable flavor, but can also use a parameter `parallelism` which specifies how many threads should be allocated to this upload.

Finally a parameter `prefix` is needed, which is the prefix of the temporary files created during a parallel upload.
We use Google storage's `compose` the merge the objects on the server-side once they are all uploaded, and therefore need the temporary files.
```scala
val effect: F[Unit] = Objects.putParallel(GCSItem("bucket", "path/to/key"), data, parallelism=16, chunkFactor=8, prefix="tmp_")
```