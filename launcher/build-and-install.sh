#!/usr/bin/env bash
# Build and install Personal Launcher on the selected ADB device.
# Optional: TV_HOST=192.168.1.50[:port], SKIP_BUILD=1, INPUT_KEY_SERVICE=1,
# HOME_KEY_SERVICE=1, RESTORE_KEY_SERVICES=1.
set -euo pipefail
cd -- "$(dirname -- "$0")"

# Keep Git Bash from converting Android component names into Windows paths.
export MSYS_NO_PATHCONV=1

package=com.sadique.launcher
apk=app/build/outputs/apk/release/app-release.apk
adb_device=(adb)
if [[ -n ${TV_HOST:-} ]]; then
    target=$TV_HOST
    case "$target" in
        *]:*|*[!:]:[0-9]*) ;;
        *) target=$target:5555 ;;
    esac
    adb connect "$target"
    adb_device+=(-s "$target")
fi

"${adb_device[@]}" get-state >/dev/null
if [[ ${SKIP_BUILD:-0} != 1 ]]; then
    ./gradlew assembleRelease
fi
if [[ ! -f $apk ]]; then
    echo "APK missing: $apk. Build it with ./gradlew assembleRelease first." >&2
    exit 1
fi

enabled_services() {
    local services
    services=$("${adb_device[@]}" shell settings get secure enabled_accessibility_services) || return
    services=${services%$'\r'}
    if [[ $services != null ]]; then
        printf '%s' "$services"
    fi
}

has_service() {
    case ":$1:" in
        *":$package/.$2:"*|*":$package/$package.$2:"*) return 0 ;;
        *) return 1 ;;
    esac
}

input_key=${INPUT_KEY_SERVICE:-0}
home_key=${HOME_KEY_SERVICE:-0}
restore_keys=${RESTORE_KEY_SERVICES:-0}
if [[ $restore_keys == 1 ]]; then
    previous=$(enabled_services)
    if has_service "$previous" InputKeyService; then input_key=1; fi
    if has_service "$previous" HomeKeyService; then home_key=1; fi
fi

"${adb_device[@]}" install -r "$apk"
if [[ $restore_keys == 1 ]]; then
    "${adb_device[@]}" shell pm grant "$package" android.permission.WRITE_SECURE_SETTINGS
fi
if [[ $input_key == 1 || $home_key == 1 ]]; then
    existing=$(enabled_services)
    services=$existing
    if [[ $input_key == 1 ]] && ! has_service "$services" InputKeyService; then
        services=${services:+$services:}$package/.InputKeyService
    fi
    if [[ $home_key == 1 ]] && ! has_service "$services" HomeKeyService; then
        services=${services:+$services:}$package/.HomeKeyService
    fi
    if [[ $services != "$existing" ]]; then
        "${adb_device[@]}" shell settings put secure enabled_accessibility_services "$services"
    fi
    "${adb_device[@]}" shell settings put secure accessibility_enabled 1
fi
"${adb_device[@]}" shell am start -W -n "$package/.MainActivity"
