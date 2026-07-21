#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import struct
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("inspect-region-headers.py")
SPEC = importlib.util.spec_from_file_location("inspect_region_headers", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def write_region(path: Path, entries: list[tuple[int, int]]) -> None:
    header = bytearray(4096)
    sectors = 2
    for local_x, local_z in entries:
        index = local_x + local_z * 32
        struct.pack_into(">I", header, index * 4, (sectors << 8) | 1)
        sectors += 1
    path.write_bytes(header + bytes(4096) + bytes(4096 * len(entries)))


class RegionHeaderToolTest(unittest.TestCase):
    def test_empty_header_and_one_chunk(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_region(root / "r.0.0.mca", [])
            self.assertEqual(set(), MODULE.inspect_directory(root))
            write_region(root / "r.0.0.mca", [(3, 4)])
            self.assertEqual({(3, 4)}, MODULE.inspect_directory(root))

    def test_negative_region_and_multiple_entries(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_region(root / "r.-1.-1.mca", [(31, 31), (0, 0)])
            self.assertEqual({(-1, -1), (-32, -32)}, MODULE.inspect_directory(root))

    def test_truncated_invalid_filename_and_invalid_offset(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "r.0.0.mca").write_bytes(b"short")
            with self.assertRaises(MODULE.RegionHeaderError):
                MODULE.inspect_directory(root)
            (root / "r.0.0.mca").unlink()
            (root / "r.bad.0.mca").write_bytes(bytes(4096))
            with self.assertRaises(MODULE.RegionHeaderError):
                MODULE.inspect_directory(root)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r.0.0.mca"
            header = bytearray(4096)
            struct.pack_into(">I", header, 0, (1 << 8) | 1)
            path.write_bytes(header)
            with self.assertRaises(MODULE.RegionHeaderError):
                MODULE.parse_region_file(path)

    def test_expected_grid_missing_and_canonical_sort(self) -> None:
        expected = MODULE.expected_grid(-1, 0, -1, 0)
        self.assertEqual(4, len(expected))
        report = MODULE.format_report({(0, 0), (-1, -1)}, expected)
        self.assertIn("missing=2", report)
        self.assertIn("PASS=false", report)
        self.assertLess(report.index("-1\t-1"), report.index("0\t0"))


if __name__ == "__main__":
    unittest.main()
