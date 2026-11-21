#!/usr/bin/env bash
# Renders the demo scenes to PNG frames and stitches them into one MP4.
# Requires ffmpeg and Gradle 8.13 on PATH. Usage: scripts/make-demo.sh [outDir] [frames] [every]
set -euo pipefail

GRADLE=${GRADLE:-gradle}
OUT=${1:-demo-out}
FRAMES=${2:-600}
EVERY=${3:-2}
FPS=${FPS:-30}

rm -rf "$OUT"
"$GRADLE" ${GRADLE_ARGS:-} :tools:demo -PdemoArgs="--scene all --out $OUT --frames $FRAMES --every $EVERY --scale 2"

mkdir -p "$OUT/clips"
: > "$OUT/clips.txt"
for dir in "$OUT"/frames_*; do
  name=$(basename "$dir")
  ffmpeg -hide_banner -loglevel error -y -framerate "$FPS" -i "$dir/frame_%04d.png" \
    -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p \
    -vf "scale=trunc(iw/2)*2:trunc(ih/2)*2" "$OUT/clips/$name.mp4"
  printf "file '%s'\n" "$OUT/clips/$name.mp4" >> "$OUT/clips.txt"
done
ffmpeg -hide_banner -loglevel error -y -f concat -safe 0 -i "$OUT/clips.txt" -c copy "$OUT/demo.mp4"
ls -la "$OUT/demo.mp4"
