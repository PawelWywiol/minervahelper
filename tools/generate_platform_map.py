#!/usr/bin/env python3
"""Maps MiNERVA (collection, platform_key) pairs onto ES-DE system slugs.

The archive is not purely emulator content: bitsavers, Internet Archive uploader dumps
and Miscellaneous are documents and loose software with no ES-DE equivalent. Those are
emitted with an empty slug and confidence=none so the app can fall back to a plain folder.

Usage: generate_platform_map.py <catalog.db> <systems.txt> <out.csv>
"""

import csv
import re
import sqlite3
import sys

NON_GAME_COLLECTIONS = {"bitsavers", "Internet Archive", "Miscellaneous"}

COLLECTION_SLUG = {
    "MAME": "arcade",
    "HBMAME": "arcade",
    "FinalBurn Neo": "arcade",
    "Eggman's Arcade Repository": "arcade",
    "TeknoParrot": "arcade",
}

# Keys whose ES-DE slug cannot be derived from the name alone.
ALIASES = {
    "IBM - PC compatible": "pc",
    "IBM/PC Compatibles": "pc",
    "IBM - PC and Compatibles": "pc",
    "eXoDOS": "dos",
    "Games": "dos",  # Total DOS Collection
    "Atari/8bit": "atari800",
    "Nintendo/Famicom & Entertainment System": "nes",
    "Nintendo/Super Famicom & Entertainment System": "snes",
    "Sinclair/ZX Spectrum": "zxspectrum",
    "Commodore/Amiga": "amiga",
    "Commodore/C64": "c64",
    "Atari/ST": "atarist",
    "Apple/II": "apple2",
    "Daphne": "daphne",
    "Hypseus Singe": "daphne",
    "Sega/Mega Drive & Genesis": "genesis",
    "Sega/Master System & Mark III": "mastersystem",
    "NEC/PC Engine & TurboGrafx-16": "tg16",
    "Nintendo - Super Nintendo Entertainment System": "snes",
    "Nintendo - Nintendo Entertainment System": "nes",
    "Acorn/BBC": "bbcmicro",
    "NEC/PC-9801": "pc98",
    "NEC/PC-8801": "pc88",
    "Sharp/X68000": "x68000",
}

# Present in the archive but absent from the ES-DE system list — deliberately unmapped.
NO_ES_DE_EQUIVALENT = {
    "Microsoft - Xbox 360 (Digital)",
    "Tandy Radio Shack/TRS-80 Model I",
    "Audio CD",
    "Multi-format/Magazines",
}

VENDORS = (
    "nintendo", "sony", "sega", "microsoft", "atari", "commodore", "nec", "snk",
    "sinclair", "apple", "ibm", "coleco", "mattel", "philips", "amstrad", "acorn",
    "bandai", "casio", "fujitsu", "sharp", "tandy radio shack", "texas instruments",
)


def normalize(text):
    text = text.lower()
    text = re.sub(r"\(.*?\)", " ", text)
    text = re.sub(r"[^a-z0-9]+", " ", text)
    return " ".join(text.split())


def strip_vendor(name):
    for vendor in VENDORS:
        if name.startswith(vendor + " "):
            return name[len(vendor) + 1:]
    return name


def load_systems(path):
    systems = {}
    for line in open(path, encoding="utf-8"):
        if ":" not in line:
            continue
        slug, name = line.split(":", 1)
        systems[slug.strip()] = name.strip()
    return systems


def build_index(systems):
    index = {}
    for slug, name in systems.items():
        for key in (normalize(name), strip_vendor(normalize(name)), slug):
            index.setdefault(key, slug)
    return index


def resolve(collection, platform_key, index):
    if collection in COLLECTION_SLUG:
        return COLLECTION_SLUG[collection], "collection"
    if platform_key in ALIASES:
        return ALIASES[platform_key], "alias"
    if collection in NON_GAME_COLLECTIONS or platform_key in NO_ES_DE_EQUIVALENT:
        return "", "none"

    key = normalize(platform_key)
    tail = normalize(platform_key.rsplit("/", 1)[-1].split(" - ", 1)[-1])
    for candidate in (key, strip_vendor(key), key.replace(" ", ""), tail, dedupe(key)):
        if candidate in index:
            return index[candidate], "auto"
    return "", "unmapped"


def dedupe(key):
    """TOSEC repeats the vendor in keys like "MSX/MSX"."""
    seen, out = set(), []
    for token in key.split():
        if token not in seen:
            seen.add(token)
            out.append(token)
    return " ".join(out)


def main(catalog, systems_file, out_csv):
    systems = load_systems(systems_file)
    index = build_index(systems)
    db = sqlite3.connect(catalog)
    rows = db.execute(
        "SELECT p.collection, p.platform_key, count(f.id) n "
        "FROM platform p JOIN dir d ON d.platform_id = p.id JOIN file f ON f.dir_id = d.id "
        "GROUP BY p.id ORDER BY n DESC"
    ).fetchall()

    stats = {}
    total = sum(r[2] for r in rows)
    with open(out_csv, "w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["collection", "platform_key", "es_slug", "confidence", "files"])
        for collection, platform_key, count in rows:
            slug, confidence = resolve(collection, platform_key, index)
            writer.writerow([collection, platform_key, slug, confidence, count])
            covered, files = stats.get(confidence, (0, 0))
            stats[confidence] = (covered + 1, files + count)

    for confidence, (covered, files) in sorted(stats.items(), key=lambda kv: -kv[1][1]):
        print(f"{confidence:<11} platforms={covered:<5} files={files:<9} ({100 * files // total}%)")


if __name__ == "__main__":
    main(*sys.argv[1:4])
