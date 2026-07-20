package com.pawelwywiol.minervahelper.spike

import android.content.Context
import com.pawelwywiol.minervahelper.data.catalog.CatalogImporter
import com.pawelwywiol.minervahelper.data.catalog.CatalogReader
import java.io.File

/**
 * M1 spike: imports a hashes.db pushed over adb, so import cost on the target device
 * is measured without waiting for the 1.64 GB download.
 */
object CatalogSpike {

    suspend fun run(context: Context, log: (String) -> Unit) {
        val source = File(context.getExternalFilesDir(null), "hashes.db")
        check(source.isFile) { "push hashes.db to ${source.absolutePath} first" }
        log("source: ${source.length() / 1_000_000} MB")

        val target = File(context.filesDir, "catalog.db")
        target.delete()

        val started = System.currentTimeMillis()
        var lastLog = 0L
        CatalogImporter().import(source, target) { p ->
            val now = System.currentTimeMillis()
            if (now - lastLog > 5_000) {
                lastLog = now
                val rate = p.imported * 1000 / (now - started + 1)
                log("${p.imported * 100 / p.total}%  ${p.imported}/${p.total}  $rate rows/s")
            }
        }
        val seconds = (System.currentTimeMillis() - started) / 1000
        log("import done in ${seconds}s, catalog=${target.length() / 1_000_000} MB")

        CatalogReader(target).use { reader ->
            check(reader.isComplete()) { "import stage not marked done" }
            log("rows=${reader.count()}")
            val t = System.currentTimeMillis()
            val hits = reader.search("zelda", limit = 20)
            log("search 'zelda': ${hits.size} shown in ${System.currentTimeMillis() - t}ms")
            hits.take(3).forEach {
                log("  ${it.name} | ${it.collection}/${it.platformKey} | so=${it.soId} | md5=${it.md5?.take(8)}")
            }

            val collections = timed(log, "collections") { reader.collections() }
            log("  ${collections.size} collections, biggest=${collections.maxBy { it.fileCount }.name}")
            val biggest = collections.maxBy { it.fileCount }
            val platforms = timed(log, "platforms of ${biggest.name}") { reader.platforms(biggest.name) }
            val topPlatform = platforms.maxBy { it.fileCount }
            log("  ${platforms.size} platforms, biggest=${topPlatform.key} (${topPlatform.fileCount} files)")
            var page = timed(log, "browse page 1") { reader.browse(topPlatform.id) }
            val paging = System.currentTimeMillis()
            repeat(100) { page = reader.browse(topPlatform.id, after = page.last()) }
            log("100 sequential pages (5000 rows): ${System.currentTimeMillis() - paging}ms")
            log("  last row: ${page.last().name}")
        }
        log("CATALOG SPIKE OK")
    }

    private inline fun <T> timed(log: (String) -> Unit, label: String, block: () -> T): T {
        val started = System.currentTimeMillis()
        val result = block()
        log("$label: ${System.currentTimeMillis() - started}ms")
        return result
    }
}
