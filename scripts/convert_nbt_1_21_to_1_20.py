#!/usr/bin/env python3
"""
NBT Structure Converter: 1.21.1 → 1.20.1

Converts Minecraft structure NBT files from 1.21.1 format to 1.20.1 format.
Only updates the DataVersion field (no item/component conversion needed
since structures use vanilla blocks only).

Usage:
    python3 convert_nbt_1_21_to_1_20.py --batch <input_dir> <output_dir>
    python3 convert_nbt_1_21_to_1_20.py <input.nbt> <output.nbt>
"""

import sys
import argparse
import gzip
import io
from pathlib import Path

try:
    import nbtlib
    from nbtlib import tag
except ImportError:
    print("Error: nbtlib not installed. Install with: pip3 install nbtlib", file=sys.stderr)
    sys.exit(1)


def save_nbt_deterministic(nbt_data: nbtlib.File, output_path: str) -> None:
    """Save NBT file with deterministic output (fixed mtime for gzip)."""
    buffer = io.BytesIO()
    nbt_data.write(buffer, nbt_data.byteorder)
    uncompressed_data = buffer.getvalue()
    compressed_data = gzip.compress(uncompressed_data, compresslevel=9, mtime=0)
    with open(output_path, 'wb') as f:
        f.write(compressed_data)


def convert_nbt_structure(input_path: str, output_path: str) -> bool:
    """Convert an NBT structure file from 1.21.1 to 1.20.1 format."""
    try:
        nbt_data = nbtlib.load(input_path)

        if "DataVersion" in nbt_data:
            original_version = nbt_data["DataVersion"]
            nbt_data["DataVersion"] = tag.Int(3465)  # Minecraft 1.20.1
            print(f"  Updated DataVersion: {original_version} -> 3465 (1.20.1)")

        save_nbt_deterministic(nbt_data, output_path)
        return True

    except Exception as e:
        print(f"  Error: {e}", file=sys.stderr)
        return False


def convert_batch(input_dir: str, output_dir: str) -> int:
    """Convert all NBT files in a directory."""
    input_path = Path(input_dir)
    output_path = Path(output_dir)

    if not input_path.exists():
        print(f"Error: Input directory does not exist: {input_dir}", file=sys.stderr)
        return 0

    output_path.mkdir(parents=True, exist_ok=True)

    nbt_files = list(input_path.glob("*.nbt"))
    if not nbt_files:
        print(f"Warning: No .nbt files found in {input_dir}")
        return 0

    print(f"Converting {len(nbt_files)} NBT files...")
    print(f"  Input:  {input_dir}")
    print(f"  Output: {output_dir}")
    print()

    success_count = 0
    for nbt_file in nbt_files:
        output_file = output_path / nbt_file.name
        print(f"Converting: {nbt_file.name}")
        if convert_nbt_structure(str(nbt_file), str(output_file)):
            success_count += 1

    print()
    print(f"Conversion complete: {success_count}/{len(nbt_files)} files converted successfully")
    return success_count


def main():
    parser = argparse.ArgumentParser(
        description="Convert Minecraft structure NBT files from 1.21.1 to 1.20.1 format"
    )
    parser.add_argument(
        "--batch", action="store_true",
        help="Batch convert all NBT files in input directory"
    )
    parser.add_argument("input", help="Input NBT file (or directory if --batch)")
    parser.add_argument("output", help="Output NBT file (or directory if --batch)")

    args = parser.parse_args()

    if args.batch:
        success_count = convert_batch(args.input, args.output)
        sys.exit(0 if success_count > 0 else 1)
    else:
        print(f"Converting: {args.input} -> {args.output}")
        success = convert_nbt_structure(args.input, args.output)
        sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
