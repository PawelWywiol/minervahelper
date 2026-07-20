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
        }
        log("CATALOG SPIKE OK")
    }
}
