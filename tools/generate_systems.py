#!/usr/bin/env python3
"""Turns systems.txt into the app's systems.csv: display name, icon shape and brand family.

The display name is what a list row shows instead of the archive's raw platform key
("Nintendo Entertainment System", not "Nintendo/Famicom & Entertainment System").

Shape and family are two independent axes, because one axis cannot carry both facts:

  shape  = what the machine *is*   (gamepad, handheld, arcade cabinet, computer, joystick)
  family = who *made* it           (nintendo, sony, sega, atari, ... -> the icon's colour)

That split is what lets a Game Boy and an NES share Nintendo red while looking different,
and a Sega NAOMI board read as "arcade cabinet, Sega blue" rather than one or the other.

No manufacturer logos: those are trademarks, and the comprehensive sets published online
carry no licence. A silhouette of a machine plus the vendor's colour is recognisable
without borrowing anyone's mark.

Usage: generate_systems.py <systems.txt> <out.csv>
"""

import csv
import sys

# --- shape: what the machine is -------------------------------------------------------

HANDHELD = {
    "gb", "gbc", "gba", "sgb", "atarilynx", "gamegear", "ngp", "ngpc", "wonderswan",
    "wonderswancolor", "nds", "n3ds", "psp", "psvita", "pokemini", "gamecom", "gamate",
    "gmaster", "supervision", "megaduck", "ngage", "lcdgames", "gameandwatch", "arduboy",
    "switch", "virtualboy", "palm", "symbian", "j2me", "vsmile",
}

ARCADE = {
    "arcade", "mame", "mess", "fba", "fbneo", "cps", "cps1", "cps2", "cps3", "naomi",
    "naomi2", "naomigd", "model2", "model3", "stv", "atomiswave", "type-x", "pcarcade",
    "consolearcade", "daphne", "laserdisc", "vpinball",
}

# The pre-NES generation: a stick on a base was the controller, and often the whole console.
JOYSTICK = {
    "atari2600", "atari5200", "atari7800", "atarixe", "vectrex", "channelf", "arcadia",
    "astrocde", "odyssey2", "videopac", "intellivision", "colecovision", "crvision",
    "pv1000", "gamate",
}

# Two grips and thumbsticks — the shape every console settled on after 1994.
GAMEPAD_MODERN = {
    "psx", "ps2", "ps3", "dreamcast", "gc", "wii", "wiiu", "xbox", "xbox360",
}

COMPUTER = {
    "amiga", "amiga600", "amiga1200", "atarist", "atari800", "apple2", "apple2gs",
    "macintosh", "c64", "vic20", "plus4", "x68000", "x1", "pc98", "pc88", "fm7",
    "fmtowns", "archimedes", "bbcmicro", "electron", "msx", "msx1", "msx2", "msxturbor",
    "dos", "pc", "windows", "windows3x", "windows9x", "samcoupe", "ti99", "oric",
    "dragon32", "tanodragon", "coco", "adam", "moto", "to8", "spectravideo", "zxspectrum",
    "zx81", "amstradcpc", "cdtv", "steam", "epic", "flash", "scummvm", "doom", "quake",
    "ports", "emulators", "openbor", "easyrpg", "lutro", "chailove", "pico8", "tic80",
    "lowresnx", "wasm4", "vircon32", "zmachine", "uzebox", "androidapps", "androidgames",
}

SHAPES = [
    (HANDHELD, "handheld"),
    (ARCADE, "arcade"),
    (JOYSTICK, "joystick"),
    (GAMEPAD_MODERN, "gamepad_modern"),
    (COMPUTER, "computer"),
]

# --- family: who made it, i.e. the icon's colour ---------------------------------------

FAMILIES = {
    "nintendo": {
        "nes", "famicom", "fds", "snes", "snesna", "sfc", "satellaview", "sufami", "sgb",
        "n64", "n64dd", "gc", "wii", "wiiu", "switch", "gb", "gbc", "gba", "nds", "n3ds",
        "virtualboy", "pokemini", "gameandwatch",
    },
    "sony": {"psx", "ps2", "ps3", "psp", "psvita"},
    "sega": {
        "mastersystem", "mark3", "sg-1000", "multivision", "genesis", "megadrive",
        "megadrivejp", "segacd", "megacd", "megacdjp", "sega32x", "sega32xjp",
        "sega32xna", "saturn", "saturnjp", "dreamcast", "gamegear", "naomi", "naomi2",
        "naomigd", "model2", "model3", "stv", "atomiswave",
    },
    "atari": {
        "atari2600", "atari5200", "atari7800", "atarixe", "atari800", "atarist",
        "atarijaguar", "atarilynx",
    },
    "snk": {"neogeo", "neogeocd", "neogeocdjp", "ngp", "ngpc"},
    "nec": {"pcengine", "pcenginecd", "supergrafx", "tg16", "tg-cd", "pcfx", "pc88", "pc98"},
    "commodore": {"c64", "vic20", "plus4", "amiga", "amiga600", "amiga1200", "amigacd32", "cdtv"},
    "sinclair": {"zxspectrum", "zx81"},
    # Microsoft's own green, extended to the PC platforms it defined.
    "microsoft": {"dos", "pc", "windows", "windows3x", "windows9x", "xbox", "xbox360"},
}


def shape_for(slug):
    for members, name in SHAPES:
        if slug in members:
            return name
    return "gamepad"


def family_for(slug):
    for name, members in FAMILIES.items():
        if slug in members:
            return name
    return "other"


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
            slug, name = slug.strip(), name.strip()
            rows.append((slug, name, shape_for(slug), family_for(slug)))

    with open(out, "w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["slug", "display_name", "shape", "family"])
        writer.writerows(sorted(rows))

    families = {}
    for _, _, _, family in rows:
        families[family] = families.get(family, 0) + 1
    print(f"{len(rows)} systems -> {out}")
    print("families: " + ", ".join(f"{k}={v}" for k, v in sorted(families.items())))


if __name__ == "__main__":
    main()
