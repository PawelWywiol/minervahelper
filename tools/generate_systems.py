#!/usr/bin/env python3
"""Turns systems.txt into the app's systems.csv: a display name and a media type per slug.

The display name is what a list row shows instead of the archive's raw platform key
("Nintendo Entertainment System", not "Nintendo/Famicom & Entertainment System").

The media type picks the row's icon. It deliberately describes *what the software came
on* rather than who made the machine: manufacturer logos are trademarks, and the
comprehensive logo sets on GitHub ship with no licence at all. Nine media shapes also
cover the whole archive, including the fifth of it that is documents and disk images
with no console behind them.

Usage: generate_systems.py <systems.txt> <out.csv>
"""

import csv
import sys

# Portable first: a handheld's shape says more at 24dp than the cartridge inside it.
HANDHELD = {
    "gb", "gbc", "gba", "sgb", "atarilynx", "gamegear", "ngp", "ngpc", "wonderswan",
    "wonderswancolor", "nds", "n3ds", "psp", "psvita", "pokemini", "gamecom", "gamate",
    "gmaster", "supervision", "megaduck", "ngage", "lcdgames", "gameandwatch", "arduboy",
    "switch", "virtualboy", "palm", "symbian", "j2me", "vsmile",
}

ARCADE = {
    "arcade", "mame", "mess", "fba", "fbneo", "cps", "cps1", "cps2", "cps3", "naomi",
    "naomi2", "naomigd", "model2", "model3", "stv", "atomiswave", "type-x", "pcarcade",
    "consolearcade", "neogeo", "daphne", "laserdisc", "vpinball",
}

DISC = {
    "psx", "ps2", "ps3", "saturn", "saturnjp", "dreamcast", "gc", "wii", "wiiu",
    "segacd", "megacd", "megacdjp", "neogeocd", "neogeocdjp", "pcenginecd", "tg-cd",
    "cdimono1", "cdtv", "amigacd32", "3do", "pcfx",
}

CARTRIDGE = {
    "nes", "famicom", "snes", "snesna", "sfc", "satellaview", "sufami", "n64", "n64dd",
    "genesis", "megadrive", "megadrivejp", "mastersystem", "mark3", "sg-1000",
    "multivision", "sega32x", "sega32xjp", "sega32xna", "pcengine", "supergrafx", "tg16",
    "atari2600", "atari5200", "atari7800", "atarixe", "atarijaguar", "colecovision",
    "intellivision", "odyssey2", "videopac", "vectrex", "channelf", "arcadia",
    "astrocde", "crvision", "scv", "pv1000", "supracan", "gx4000", "uzebox",
}

# Cassette-loading home computers — the tape is the defining experience of these.
TAPE = {"zxspectrum", "zx81", "amstradcpc"}

FLOPPY = {
    "amiga", "amiga600", "amiga1200", "atarist", "apple2", "apple2gs", "c64", "x68000",
    "x1", "pc98", "pc88", "fm7", "fmtowns", "archimedes", "bbcmicro", "electron", "msx",
    "msx1", "msx2", "msxturbor", "dos", "pc", "fds", "samcoupe", "plus4", "vic20",
    "ti99", "oric", "dragon32", "tanodragon", "coco", "adam", "moto", "to8",
    "spectravideo", "macintosh", "windows3x", "atari800",
}

# Modern platforms and engines: nothing physical ever shipped, so no medium fits.
COMPUTER = {
    "windows", "windows9x", "steam", "epic", "androidapps", "androidgames", "flash",
    "scummvm", "doom", "quake", "ports", "emulators", "openbor", "easyrpg", "lutro",
    "chailove", "pico8", "tic80", "lowresnx", "wasm4", "vircon32", "zmachine",
}

GROUPS = [
    (HANDHELD, "handheld"),
    (ARCADE, "arcade"),
    (DISC, "disc"),
    (CARTRIDGE, "cartridge"),
    (TAPE, "tape"),
    (FLOPPY, "floppy"),
    (COMPUTER, "computer"),
]


def media_for(slug):
    for members, name in GROUPS:
        if slug in members:
            return name
    return "unknown"


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    source, out = sys.argv[1], sys.argv[2]

    rows = []
    with open(source, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or ":" not in line:
                continue
            slug, name = line.split(":", 1)
            rows.append((slug.strip(), name.strip(), media_for(slug.strip())))

    with open(out, "w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["slug", "display_name", "media"])
        writer.writerows(sorted(rows))

    unknown = [slug for slug, _, media in rows if media == "unknown"]
    print(f"{len(rows)} systems -> {out}")
    print(f"unclassified ({len(unknown)}): {', '.join(unknown) or 'none'}")


if __name__ == "__main__":
    main()
