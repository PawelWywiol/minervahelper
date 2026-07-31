package io.github.pawelwywiol.minervahelper.data.placement

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.zip.ZipFile

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
        val dir = destinationDir(root, folder)

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

    /**
     * Unpacks a staged archive straight into the user's folder and drops the archive itself.
     *
     * The archive is never placed first: writing it out only to delete it again would cost a second
     * pass over several gigabytes, and would briefly leave a file the emulator cannot open in a
     * folder the frontend is scanning.
     *
     * Returns the names written. Nothing is written if the archive turns out to hold no usable entry.
     */
    fun unpack(root: Uri, folder: String, source: File): List<String> {
        val dir = destinationDir(root, folder)
        val placed = mutableListOf<String>()

        ZipFile(source).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/')
                if (name.isBlank() || name.startsWith("._") || entry.name.startsWith("__MACOSX/")) continue

                dir.findFile(name)?.delete()
                val target = requireNotNull(dir.createFile(MIME, name)) { "cannot create $name in $folder" }
                app.contentResolver.openOutputStream(target.uri).use { sink ->
                    requireNotNull(sink) { "cannot open $name for writing" }
                    zip.getInputStream(entry).use { it.copyTo(sink) }
                }
                placed += name
            }
        }
        source.delete()
        return placed
    }

    private fun destinationDir(root: Uri, folder: String): DocumentFile {
        val tree = requireNotNull(DocumentFile.fromTreeUri(app, root)) { "root folder unavailable" }
        require(tree.canWrite()) { "no write access to the chosen folder" }
        return tree.findFile(folder)?.takeIf { it.isDirectory }
            ?: requireNotNull(tree.createDirectory(folder)) { "cannot create $folder" }
    }

    /** True when the destination already holds a file of this name and size (R38). */
    fun exists(root: Uri, folder: String, name: String, size: Long): Boolean =
        DocumentFile.fromTreeUri(app, root)
            ?.findFile(folder)?.takeIf { it.isDirectory }
            ?.findFile(name)
            ?.let { it.isFile && it.length() == size } ?: false

    /**
     * Moves one placed file between destination folders (R39). Copy-then-delete rather than a
     * provider move: the old and new folder may live on different storage trees.
     * Returns false when the source file is not there any more — nothing to move is not an error.
     */
    fun move(fromRoot: Uri, fromFolder: String, toRoot: Uri, toFolder: String, name: String): Boolean {
        val origin = DocumentFile.fromTreeUri(app, fromRoot)
            ?.findFile(fromFolder)?.takeIf { it.isDirectory }
            ?: return false
        // An unpacked disc is several files — a .cue and its tracks — recorded under the one name the
        // emulator opens. Moving that name alone would leave the game behind in pieces, so the whole
        // set the archive produced travels together. Redump and No-Intro name every track after the
        // game, which is what makes the group recognisable.
        val sources = origin.listFiles().filter {
            it.isFile && it.name?.let { file -> file == name || file.startsWith(name.substringBeforeLast('.')) } == true
        }
        if (sources.isEmpty()) return false

        val targetTree = requireNotNull(DocumentFile.fromTreeUri(app, toRoot)) { "destination unavailable" }
        require(targetTree.canWrite()) { "no write access to the destination" }
        val dir = targetTree.findFile(toFolder)?.takeIf { it.isDirectory }
            ?: requireNotNull(targetTree.createDirectory(toFolder)) { "cannot create $toFolder" }

        for (source in sources) {
            val fileName = source.name ?: continue
            dir.findFile(fileName)?.delete()
            val target = requireNotNull(dir.createFile(MIME, fileName)) { "cannot create $fileName in $toFolder" }
            app.contentResolver.openOutputStream(target.uri).use { sink ->
                requireNotNull(sink) { "cannot open $fileName for writing" }
                app.contentResolver.openInputStream(source.uri).use { input ->
                    requireNotNull(input) { "cannot read $fileName" }
                    input.copyTo(sink)
                }
            }
            source.delete()
        }
        return true
    }

    private companion object {
        /** ROMs have no meaningful MIME type; anything more specific would only mislead pickers. */
        const val MIME = "application/octet-stream"
    }
}
