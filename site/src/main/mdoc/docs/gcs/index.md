---
layout: docs
title: "Google Cloud"
---
# {{page.title}}
The Google Cloud Storage API has been written on top of the rest client using http4s with the Java [async-http-client](https://github.com/AsyncHttpClient/async-http-client) library.

If feasible, building on top of the official API would be desirable, but as of now, it looks like official client uses the either the synchronous version of [apache-http](https://github.com/googleapis/google-http-java-client/blob/master/google-http-client/src/main/java/com/google/api/client/http/apache/ApacheHttpTransport.java) or javanet [javanet](https://github.com/googleapis/google-http-java-client/blob/master/google-http-client/src/main/java/com/google/api/client/http/javanet/NetHttpTransport.java).\
Additionally the library has very little notion of when [effects should be carried out](https://github.com/googleapis/java-storage/blob/d8425808976edde3aefee4adee32905b28987265/google-cloud-storage/src/main/java/com/google/cloud/storage/spi/v1/HttpStorageRpc.java#L401).
 