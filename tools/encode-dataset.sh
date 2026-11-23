#!/usr/bin/env bash
set -euo pipefail

# Dataset Encoding Matrix Generator
# Encodes master PNGs into WebP, JXL, and AVIF matrix formats

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MASTER_DIR="${1:-$ROOT_DIR/dataset/masters}"
OUT_DIR="${2:-$ROOT_DIR/dataset/encoded}"

mkdir -p "$OUT_DIR"

echo "=========================================="
echo "Komikku Dataset Encoding Matrix Generator"
echo "Masters: $MASTER_DIR"
echo "Output:  $OUT_DIR"
echo "=========================================="

if [ ! -d "$MASTER_DIR" ] || [ -z "$(ls -A "$MASTER_DIR" 2>/dev/null)" ]; then
    echo "Notice: Master directory is empty. Generate synthetic images or copy master PNGs first."
    exit 0
fi

for img in "$MASTER_DIR"/*.png; do
    [ -f "$img" ] || continue
    BASENAME="$(basename "$img" .png)"
    echo "Processing $BASENAME..."

    # 1. WebP Lossless & Lossy
    if command -v cwebp &>/dev/null; then
        cwebp -lossless -m 6 -mt "$img" -o "$OUT_DIR/${BASENAME}_webp_lossless.webp"
        cwebp -q 85 -m 4 -mt "$img" -o "$OUT_DIR/${BASENAME}_webp_q85_m4.webp"
        cwebp -q 90 -m 6 -mt "$img" -o "$OUT_DIR/${BASENAME}_webp_q90_m6.webp"
    fi

    # 2. JPEG XL
    if command -v cjxl &>/dev/null; then
        cjxl "$img" "$OUT_DIR/${BASENAME}_jxl_lossless.jxl" -e 7 --modular
        cjxl "$img" "$OUT_DIR/${BASENAME}_jxl_d10.jxl" -d 1.0 -e 7
        cjxl "$img" "$OUT_DIR/${BASENAME}_jxl_d05.jxl" -d 0.5 -e 7
    fi

    # 3. AVIF
    if command -v avifenc &>/dev/null; then
        avifenc --lossless -s 4 "$img" "$OUT_DIR/${BASENAME}_avif_lossless.avif"
        avifenc -q 85 -s 4 --yuv 444 "$img" "$OUT_DIR/${BASENAME}_avif_q85_yuv444.avif"
        avifenc -q 90 -s 4 --yuv 420 "$img" "$OUT_DIR/${BASENAME}_avif_q90_yuv420.avif"
    fi
done

echo "✓ Dataset encoding completed."
