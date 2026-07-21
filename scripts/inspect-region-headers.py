#!/usr/bin/env python3
"""Read-only Anvil location-table inspection for Phase 4B1."""

from __future__ import annotations

import argparse
import re
import struct
from pathlib import Path

REGION_NAME = re.compile(r"r\.(-?\d+)\.(-?\d+)\.mca")
SECTOR_BYTES = 4096


class RegionHeaderError(ValueError):
    pass


def parse_region_file(path: Path) -> set[tuple[int, int]]:
    match = REGION_NAME.fullmatch(path.name)
    if match is None:
        raise RegionHeaderError(f"invalid region filename: {path.name}")
    region_x, region_z = (int(value) for value in match.groups())
    size = path.stat().st_size
    if size < SECTOR_BYTES:
        raise RegionHeaderError(f"truncated header: {path}")
    with path.open("rb") as handle:
        header = handle.read(SECTOR_BYTES)
    chunks: set[tuple[int, int]] = set()
    for index in range(1024):
        location = struct.unpack_from(">I", header, index * 4)[0]
        sector_offset = location >> 8
        sector_count = location & 0xFF
        if sector_offset == 0 and sector_count == 0:
            continue
        if sector_offset < 2 or sector_count == 0:
            raise RegionHeaderError(f"invalid location entry: {path}:{index}")
        if (sector_offset + sector_count) * SECTOR_BYTES > size:
            raise RegionHeaderError(f"location beyond file: {path}:{index}")
        local_x = index & 31
        local_z = index >> 5
        chunks.add((region_x * 32 + local_x, region_z * 32 + local_z))
    return chunks


def inspect_directory(region_directory: Path) -> set[tuple[int, int]]:
    if not region_directory.is_dir():
        raise RegionHeaderError(f"region directory missing: {region_directory}")
    chunks: set[tuple[int, int]] = set()
    files = sorted(region_directory.iterdir(), key=lambda item: item.name)
    for path in files:
        if not path.is_file():
            continue
        if path.suffix == ".mca" and REGION_NAME.fullmatch(path.name) is None:
            raise RegionHeaderError(f"invalid region filename: {path.name}")
        if REGION_NAME.fullmatch(path.name) is None:
            continue
        overlap = chunks.intersection(parse_region_file(path))
        if overlap:
            raise RegionHeaderError(f"duplicate chunk coordinates: {sorted(overlap)}")
        chunks.update(parse_region_file(path))
    return chunks


def expected_grid(minimum_x: int, maximum_x: int, minimum_z: int, maximum_z: int) -> set[tuple[int, int]]:
    if minimum_x > maximum_x or minimum_z > maximum_z:
        raise RegionHeaderError("invalid expected grid")
    return {
        (chunk_x, chunk_z)
        for chunk_z in range(minimum_z, maximum_z + 1)
        for chunk_x in range(minimum_x, maximum_x + 1)
    }


def format_report(chunks: set[tuple[int, int]], expected: set[tuple[int, int]]) -> str:
    missing = expected - chunks
    extra = chunks - expected
    lines = ["chunkX\tchunkZ"]
    lines.extend(f"{x}\t{z}" for x, z in sorted(chunks, key=lambda value: (value[1], value[0])))
    lines.extend(
        [
            f"present={len(chunks)}",
            f"expected={len(expected)}",
            f"missing={len(missing)}",
            f"extra={len(extra)}",
            "missingChunks=" + ";".join(f"{x},{z}" for x, z in sorted(missing, key=lambda value: (value[1], value[0]))),
            "extraChunks=" + ";".join(f"{x},{z}" for x, z in sorted(extra, key=lambda value: (value[1], value[0]))),
            "PASS=" + str(not missing).lower(),
        ]
    )
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region-dir", required=True, type=Path)
    parser.add_argument("--minimum-chunk-x", required=True, type=int)
    parser.add_argument("--maximum-chunk-x", required=True, type=int)
    parser.add_argument("--minimum-chunk-z", required=True, type=int)
    parser.add_argument("--maximum-chunk-z", required=True, type=int)
    parser.add_argument("--output", type=Path)
    arguments = parser.parse_args()
    chunks = inspect_directory(arguments.region_dir)
    expected = expected_grid(
        arguments.minimum_chunk_x,
        arguments.maximum_chunk_x,
        arguments.minimum_chunk_z,
        arguments.maximum_chunk_z,
    )
    report = format_report(chunks, expected)
    if arguments.output:
        arguments.output.write_text(report, encoding="utf-8")
    else:
        print(report, end="")
    return 0 if expected.issubset(chunks) else 1


if __name__ == "__main__":
    raise SystemExit(main())
