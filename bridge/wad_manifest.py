"""Opt-in OFFLINE WAD inspection using verified WizWalker Wad APIs.
Lists archive entries only. It does not decode serialized item templates or attach to a game.
Install the separately licensed optional dependency yourself; see docs/WIZWALKER.md.
"""
import argparse
import asyncio
import hashlib
import json
from pathlib import Path


async def manifest(wad_path: Path) -> dict:
    from wizwalker.file_readers.wad import Wad
    archive = Wad(wad_path)
    await archive.open()
    try:
        entries = []
        for name in await archive.names():
            info = await archive.get_file_info(name)
            entries.append({"name": name, "size": info.size, "compressed": info.is_zip,
                            "crc": info.crc, "offset": info.offset})
        with wad_path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        return {"schemaVersion": 1, "kind": "wad-manifest", "archive": wad_path.name,
                "sha256": digest, "entries": entries,
                "warning": "Archive entries are not decoded gear records. No completeness/ownership claims."}
    finally:
        archive.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--wad", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = asyncio.run(manifest(args.wad))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
