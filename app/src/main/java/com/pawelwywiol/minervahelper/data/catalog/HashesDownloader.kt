package com.pawelwywiol.minervahelper.data.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

/**
 * Downloads hashes.db (~1.64 GB) with Range resume. The server advertises Accept-Ranges
 * and a stable ETag, so a partial file is resumed only while the ETag still matches.
 */
class HashesDownloader(private val http: OkHttpClient) {

    data class Progress(val downloaded: Long, val total: Long)

    suspend fun download(
        target: File,
        onProgress: suspend (Progress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val tagFile = File(target.parentFile, target.name + ".etag")
        val knownTag = tagFile.takeIf { it.isFile }?.readText()

        val remote = head()
        if (knownTag != null && knownTag != remote.etag) {
            target.delete()
            tagFile.delete()
        }
        remote.etag?.let(tagFile::writeText)

        var have = if (target.isFile) target.length() else 0L
        if (have == remote.length) return@withContext target
        if (have > remote.length) {
            target.delete()
            have = 0L
        }

        val request = Request.Builder().url(URL).apply {
            if (have > 0) header("Range", "bytes=$have-")
        }.build()

        http.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            if (have > 0 && res.code != 206) {
                have = 0L
                target.delete()
            }
            val body = res.body ?: throw IOException("empty body")
            RandomAccessFile(target, "rw").use { out ->
                out.seek(have)
                val buf = ByteArray(256 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        have += n
                        onProgress(Progress(have, remote.length))
                    }
                }
            }
        }

        if (target.length() != remote.length) {
            throw IOException("size mismatch: ${target.length()} != ${remote.length}")
        }
        target
    }

    private data class Remote(val length: Long, val etag: String?)

    private fun head(): Remote {
        http.newCall(Request.Builder().url(URL).head().build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val length = res.header("Content-Length")?.toLongOrNull()
                ?: throw IOException("no Content-Length")
            return Remote(length, res.header("ETag"))
        }
    }

    private companion object {
        const val URL = "https://minerva-archive.org/assets/hashes.db"
    }
}
