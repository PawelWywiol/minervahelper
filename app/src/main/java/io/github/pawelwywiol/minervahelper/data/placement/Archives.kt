package io.github.pawelwywiol.minervahelper.data.placement

import java.io.File
import java.util.zip.ZipFile

/**
 * Whether a downloaded archive has to be opened before an emulator can use what is inside.
 *
 * Cartridge cores read a ROM straight out of a `.zip`, so unpacking those would only waste space.
 * Disc images are the opposite: no libretro disc core, and no standalone PS2, GameCube, Wii,
 * Dreamcast or PSP emulator, will mount a compressed archive — and Mupen64Plus FZ will not take one
 * either. Left packed, those downloads reach the folder and simply refuse to start.
 *
 * The decision is made from what the archive actually holds rather than from a list of platforms,
 * because the archive is the thing the emulator has to open, and the same console ships as both
 * cartridge dumps and disc images across the collections here.
 */
private val UNPACKED_EXTENSIONS = setOf(
    // disc images
    "iso", "chd", "cue", "gdi", "cdi", "rvz", "gcm", "wbfs", "nkit", "cso", "img", "mdf", "ccd",
    // Nintendo 64 — Mupen64Plus FZ takes the raw ROM only
    "z64", "n64", "v64", "ndd",
)

/** Every name inside the archive, or empty when it is not a readable zip. */
private fun entryNames(archive: File): List<String> =
    runCatching {
        ZipFile(archive).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filterNot { it.startsWith("__MACOSX/") || it.substringAfterLast('/').startsWith("._") }
                .toList()
        }
    }.getOrDefault(emptyList())

/**
 * True when [name] is an archive whose contents an emulator would have to be handed directly.
 * [archive] is the staged file, still in private storage.
 */
fun needsUnpacking(name: String, archive: File): Boolean {
    if (!name.endsWith(".zip", ignoreCase = true)) return false
    val names = entryNames(archive)
    return names.isNotEmpty() &&
        names.any { it.substringAfterLast('.', "").lowercase() in UNPACKED_EXTENSIONS }
}

/**
 * Which of the unpacked files the user actually starts. A disc arrives as a descriptor and its
 * tracks, and it is the descriptor the emulator is pointed at — the tracks are read through it and
 * launching one directly does nothing.
 */
fun playableName(written: List<String>): String =
    written.firstOrNull { it.substringAfterLast('.', "").lowercase() in PLAYABLE_EXTENSIONS }
        ?: written.first()

private val PLAYABLE_EXTENSIONS = setOf(
    "m3u", "cue", "gdi", "ccd", "chd", "iso", "rvz", "gcm", "wbfs", "cdi", "cso",
    "z64", "n64", "v64", "ndd",
)
