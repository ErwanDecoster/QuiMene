#!/bin/bash
# Captures des fiches App Store et Play Store (doc 10 « Captures des stores »).
#
#   Scripts/store-screenshots.sh [ios|android|slides|all]     (all par défaut)
#
# Les deux apps affichent les mêmes données de démo (spec/screenshots/demo-data.json) et les
# mêmes écrans, dans chaque langue de LOCALES. Résultat dans store-screenshots/ (non versionné) :
#   app-store/<langue>/<iphone-6.9|ipad-13>/<écran>.png            captures brutes
#   play-store/<langue>/<phone|tablet-7|tablet-10>/<écran>.png
#   slides/<store>/<langue>/<appareil>/<écran>.png                  slides à publier (store/slides)
#
# Variables facultatives :
#   LOCALES  codes de langue séparés par des virgules (défaut : fr-FR,en-US,es-ES,de-DE,it-IT)
#   OUTPUT   dossier de sortie (défaut : store-screenshots/ à la racine du dépôt)
#
# iOS : simulateurs dédiés « Qui Mène Screenshots … », créés au besoin et effacés à chaque
# lancement (aucun effet sur les simulateurs de développement), redémarrés dans chaque langue
# (la date de la barre d'état iPad et le clavier suivent la langue du système), barre d'état à 9:41.
# Android : rendu Robolectric sur la JVM, sans émulateur (StoreScreenshotsTest).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCALES="${LOCALES:-fr-FR,en-US,es-ES,de-DE,it-IT}"
OUTPUT="${OUTPUT:-$ROOT/store-screenshots}"
WORK="$ROOT/store-screenshots/.work"

# Formats exigés par App Store Connect : iPhone 6,9" (1320 × 2868) et iPad 13" (2064 × 2752).
IOS_DEVICES=(
    "iphone-6.9|com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro-Max"
    "ipad-13|com.apple.CoreSimulator.SimDeviceType.iPad-Pro-13-inch-M5-12GB"
)

log() { printf '\n==> %s\n' "$*"; }

latest_ios_runtime() {
    xcrun simctl list runtimes -j | python3 -c '
import json, sys
runtimes = [r for r in json.load(sys.stdin)["runtimes"] if r["platform"] == "iOS" and r["isAvailable"]]
runtimes.sort(key=lambda r: [int(x) for x in r["version"].split(".")])
print(runtimes[-1]["identifier"])'
}

# Crée le simulateur au premier passage, puis le réutilise : son nom suffit à le retrouver.
simulator_udid() {
    local name="$1" device_type="$2" runtime="$3"
    local udid
    udid="$(xcrun simctl list devices -j | python3 -c '
import json, sys
name, runtime = sys.argv[1], sys.argv[2]
for device in json.load(sys.stdin)["devices"].get(runtime, []):
    if device["name"] == name and device["isAvailable"]:
        print(device["udid"])
        break' "$name" "$runtime")"
    if [ -z "$udid" ]; then
        udid="$(xcrun simctl create "$name" "$device_type" "$runtime")"
    fi
    echo "$udid"
}

# Langue du système, pas seulement de l'app : elle ne s'applique qu'au redémarrage, qui efface
# aussi la barre d'état imposée.
prepare_simulator() {
    local udid="$1" locale="$2"
    xcrun simctl spawn "$udid" defaults write -g AppleLanguages -array "${locale%%-*}"
    xcrun simctl spawn "$udid" defaults write -g AppleLocale -string "${locale/-/_}"
    xcrun simctl shutdown "$udid"
    xcrun simctl boot "$udid"
    xcrun simctl bootstatus "$udid" -b > /dev/null
    xcrun simctl status_bar "$udid" override --time 9:41 --dataNetwork wifi \
        --wifiMode active --wifiBars 3 --cellularMode active --cellularBars 4 \
        --batteryState charged --batteryLevel 100
}

# Range chaque pièce jointe « <langue>_<écran> » du résultat de test sous <langue>/<appareil>/.
export_ios_attachments() {
    local result="$1" device="$2" exported="$WORK/attachments"
    rm -rf "$exported"
    xcrun xcresulttool export attachments --path "$result" --output-path "$exported" > /dev/null
    python3 - "$exported" "$OUTPUT/app-store" "$device" <<'PY'
import json, os, shutil, sys
exported, destination, device = sys.argv[1:]
count = 0
for test in json.load(open(os.path.join(exported, "manifest.json"))):
    for attachment in test["attachments"]:
        # « fr-FR_01-partie_0_<UUID>.png » : nom de la pièce jointe, puis index et UUID. Les
        # autres pièces jointes (description d'un avertissement…) ne sont pas des captures.
        if not attachment["exportedFileName"].endswith(".png"):
            continue
        name = attachment["suggestedHumanReadableName"].rsplit("_", 2)[0]
        locale, screen = name.split("_", 1)
        folder = os.path.join(destination, locale, device)
        os.makedirs(folder, exist_ok=True)
        shutil.copy(os.path.join(exported, attachment["exportedFileName"]),
                    os.path.join(folder, screen + ".png"))
        count += 1
print(f"{count} captures {device}")
PY
}

capture_ios() {
    local project="$ROOT/apple/App/QuiMene.xcodeproj"
    local derived="$WORK/ios-derived"
    local runtime
    runtime="$(latest_ios_runtime)"

    log "iOS — compilation des tests ($runtime)"
    xcodebuild build-for-testing -project "$project" -scheme QuiMene \
        -destination 'generic/platform=iOS Simulator' -derivedDataPath "$derived" -quiet

    rm -rf "$OUTPUT/app-store"
    for entry in "${IOS_DEVICES[@]}"; do
        local device="${entry%%|*}" device_type="${entry#*|}"
        local udid
        udid="$(simulator_udid "Qui Mène Screenshots $device" "$device_type" "$runtime")"
        xcrun simctl shutdown "$udid" 2> /dev/null || true
        xcrun simctl erase "$udid"
        xcrun simctl boot "$udid"
        xcrun simctl bootstatus "$udid" -b > /dev/null
        xcrun simctl ui "$udid" appearance light

        local locale
        for locale in ${LOCALES//,/ }; do
            log "iOS — $device, $locale"
            prepare_simulator "$udid" "$locale"
            local result="$WORK/ios-$device-$locale.xcresult"
            rm -rf "$result"
            TEST_RUNNER_QUIMENE_SCREENSHOT_LOCALES="$locale" xcodebuild test-without-building \
                -project "$project" -scheme QuiMene -destination "id=$udid" \
                -derivedDataPath "$derived" -only-testing:QuiMeneUITests/StoreScreenshotTests \
                -collect-test-diagnostics never -resultBundlePath "$result" -quiet
            export_ios_attachments "$result" "$device"
        done
        xcrun simctl shutdown "$udid"
    done
}

# Chaque capture dans un cadre d'appareil, sous son titre traduit (store/slides/captions.json).
render_slides() {
    log "Slides des fiches"
    rm -rf "$OUTPUT/slides"
    node "$ROOT/store/slides/render.mjs" "$OUTPUT" "$OUTPUT/slides"
}

capture_android() {
    log "Android — rendu Robolectric"
    rm -rf "$OUTPUT/play-store"
    (cd "$ROOT/android" && ./gradlew :app:testDebugUnitTest --tests '*StoreScreenshotsTest' \
        -Pquimene.screenshots.locales="$LOCALES" \
        -Pquimene.screenshots.output="$OUTPUT/play-store" --rerun --quiet)
    echo "$(find "$OUTPUT/play-store" -name '*.png' | wc -l | tr -d ' ') captures Android"
}

mkdir -p "$WORK"
case "${1:-all}" in
    ios) capture_ios ;;
    android) capture_android ;;
    slides) render_slides ;;
    all)
        capture_ios
        capture_android
        render_slides
        ;;
    *)
        echo "usage : $0 [ios|android|slides|all]" >&2
        exit 2
        ;;
esac
log "Captures dans $OUTPUT"
