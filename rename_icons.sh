#!/bin/bash
RES_DIR="/home/jakkal/projects/AudioBookify/app/src/main/res"
DENSITIES=("mdpi" "hdpi" "xhdpi" "xxhdpi" "xxxhdpi")

for density in "${DENSITIES[@]}"; do
    FILE="$RES_DIR/mipmap-$density/ic_launcher.webp"
    if [ -f "$FILE" ]; then
        mv "$FILE" "$RES_DIR/mipmap-$density/ic_launcher_foreground_image.webp"
        echo "Renamed $density icon"
    fi
done
