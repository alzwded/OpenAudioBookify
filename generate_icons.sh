#!/bin/bash

# Configuration
INPUT_IMAGE="$1"
OUTPUT_DIR="app/src/main/res"
declare -a BASE_NAMES=("ic_launcher" "ic_launcher_round" "ic_launcher_foreground_image")

if [ -z "$INPUT_IMAGE" ]; then
    echo "Usage: ./generate_icons.sh <path_to_high_res_image>"
    exit 1
fi

declare -A RESOLUTIONS=(
    ["mipmap-mdpi"]="48x48"
    ["mipmap-hdpi"]="72x72"
    ["mipmap-xhdpi"]="96x96"
    ["mipmap-xxhdpi"]="144x144"
    ["mipmap-xxxhdpi"]="192x192"
)

for DIR in "${!RESOLUTIONS[@]}"; do
    SIZE=${RESOLUTIONS[$DIR]}
    TARGET_DIR="$OUTPUT_DIR/$DIR"
    mkdir -p "$TARGET_DIR"
    echo "Generating $SIZE icon for $DIR..."
    for BASE_NAME in "${BASE_NAMES[@]}"; do
        ffmpeg -i "$INPUT_IMAGE" -vf "scale=${SIZE/:/x}:flags=lanczos" -lossless 1 "$TARGET_DIR/${BASE_NAME}.webp" -y > /dev/null 2>&1
    done
done

echo "Done! Icons generated as ${BASE_NAME}.webp in $OUTPUT_DIR."
