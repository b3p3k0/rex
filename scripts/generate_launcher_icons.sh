#!/usr/bin/env bash

set -euo pipefail

SRC_IMAGE=${1:-rex.png}
OUT_ROOT="app/src/main/res"
PLAY_STORE_OUT="app/src/main/ic_launcher-playstore.png"
BG_COLOR=${ICON_BG_COLOR:-#0F172A}
FOREGROUND_SCALE_PERCENT=${ICON_FG_SCALE_PERCENT:-88}

if [[ ! -f "$SRC_IMAGE" ]]; then
  echo "Source image not found: $SRC_IMAGE" >&2
  exit 1
fi

if command -v magick >/dev/null 2>&1; then
  IM_CMD=(magick)
elif command -v convert >/dev/null 2>&1; then
  IM_CMD=(convert)
else
  echo "ImageMagick (magick/convert) is required." >&2
  exit 1
fi

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

base_image="$tmp_dir/base.png"
"${IM_CMD[@]}" "$SRC_IMAGE" -alpha on -background none \
  -resize 1024x1024 -gravity center -extent 1024x1024 "$base_image"

declare -a DENSITIES=(
  "mdpi:48"
  "hdpi:72"
  "xhdpi:96"
  "xxhdpi:144"
  "xxxhdpi:192"
)

for entry in "${DENSITIES[@]}"; do
  density=${entry%%:*}
  size=${entry##*:}
  out_dir="$OUT_ROOT/mipmap-$density"
  mkdir -p "$out_dir"

  rm -f "$out_dir"/ic_launcher.* "$out_dir"/ic_launcher_round.* "$out_dir"/ic_launcher_foreground.*

  bg_image="$tmp_dir/bg_${density}.png"
  fg_image="$tmp_dir/fg_${density}.png"
  merged_image="$tmp_dir/m_${density}.png"

  "${IM_CMD[@]}" -size ${size}x${size} "xc:$BG_COLOR" "$bg_image"

  fg_size=$(( size * FOREGROUND_SCALE_PERCENT / 100 ))
  if (( fg_size < 1 )); then
    fg_size=$size
  fi

  "${IM_CMD[@]}" "$base_image" -resize ${fg_size}x${fg_size} -gravity center -extent ${fg_size}x${fg_size} "$fg_image"
  "${IM_CMD[@]}" "$bg_image" "$fg_image" -gravity center -compose over -composite "$merged_image"

  cp "$merged_image" "$out_dir/ic_launcher.png"

  cp "$merged_image" "$out_dir/ic_launcher_round.png"
done

foreground_out="$OUT_ROOT/mipmap-anydpi-v26/ic_launcher_foreground.png"
mkdir -p "$OUT_ROOT/mipmap-anydpi-v26"
"${IM_CMD[@]}" "$base_image" -resize 432x432 -gravity center -extent 432x432 "$foreground_out"

for entry in "${DENSITIES[@]}"; do
  density=${entry%%:*}
  out_dir="$OUT_ROOT/mipmap-$density"
  cp "$foreground_out" "$out_dir/ic_launcher_foreground.png"
done

"${IM_CMD[@]}" "$base_image" -resize 512x512 -gravity center -extent 512x512 "$tmp_dir/play.png"
"${IM_CMD[@]}" -size 512x512 "xc:$BG_COLOR" "$tmp_dir/play_bg.png"
"${IM_CMD[@]}" "$tmp_dir/play_bg.png" "$tmp_dir/play.png" -gravity center -compose over -composite "$PLAY_STORE_OUT"

echo "Launcher icons generated from $SRC_IMAGE"
