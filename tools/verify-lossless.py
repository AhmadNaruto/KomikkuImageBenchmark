#!/usr/bin/env python3
"""
Komikku Image Benchmark: Exact Pixel Verifier
Verifies bit-exact pixel matching between original master and decoded lossless images.
"""

import sys
import os

def compare_raw_bytes(file_a: str, file_b: str):
    if not os.path.exists(file_a) or not os.path.exists(file_b):
        print(f"Error: One of the files does not exist ({file_a}, {file_b})")
        return False

    size_a = os.path.getsize(file_a)
    size_b = os.path.getsize(file_b)

    if size_a != size_b:
        print(f"Size mismatch: {size_a} vs {size_b} bytes")

    chunk_size = 65536
    diff_count = 0
    total_bytes = 0

    with open(file_a, "rb") as fa, open(file_b, "rb") as fb:
        while True:
            ca = fa.read(chunk_size)
            cb = fb.read(chunk_size)
            if not ca and not cb:
                break
            min_len = min(len(ca), len(cb))
            for i in range(min_len):
                if ca[i] != cb[i]:
                    diff_count += 1
            diff_count += abs(len(ca) - len(cb))
            total_bytes += max(len(ca), len(cb))

    if diff_count == 0:
        print(f"✓ EXACT MATCH: 0 byte differences across {total_bytes} bytes.")
        return True
    else:
        print(f"✗ MISMATCH: {diff_count} differing bytes out of {total_bytes} bytes.")
        return False

def main():
    if len(sys.argv) < 3:
        print("Usage: verify-lossless.py <master_image> <decoded_image>")
        sys.exit(1)

    file_a = sys.argv[1]
    file_b = sys.argv[2]
    match = compare_raw_bytes(file_a, file_b)
    sys.exit(0 if match else 1)

if __name__ == "__main__":
    main()
