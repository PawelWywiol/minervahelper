package com.pawelwywiol.minervahelper.data.placement

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Moves a downloaded file out of the app's staging area into the user's folder.
 *
 * Called only after the checksum matched (R21). Everything the torrent engine writes stays in
 * private storage until then, so a corrupt or half-finished file can never appear in a folder the
 * emulator scans.
 */
class FilePlacement(context: Context) {

    private val app = context.applicationContext

    fun place(root: Uri, folder: String, name: String, source: File): Uri {
        val tree = requireNotNull(DocumentFile.fromTreeUri(app, root)) { "root folder unavailable" }
        require(tree.canWrite()) { "no write access to the chosen folder" }

        val dir = tree.findFile(folder)?.takeIf { it.isDirectory }
            ?: requireNotNull(tree.createDirectory(folder)) { "cannot create $folder" }

        // Replacing rather than letting the framework append " (1)": a re-download of the same ROM
        // is a repair, and a folder full of numbered duplicates is worse than an overwrite.
        dir.findFile(name)?.delete()
        val target = requireNotNull(dir.createFile(MIME, name)) { "cannot create $name in $folder" }

        app.contentResolver.openOutputStream(target.uri).use { sink ->
            requireNotNull(sink) { "cannot open $name for writing" }
            source.inputStream().use { it.copyTo(sink) }
        }
        source.delete()
        return target.uri
    }

    private companion object {
        /** ROMs have no meaningful MIME type; anything more specific would only mislead pickers. */
        const val MIME = "application/octet-stream"
    }
}
