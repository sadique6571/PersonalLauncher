#!/system/bin/sh
# Optional shell-only injector. The APK is a classpath payload, not installed/replaced.
STATE=${PLV_FAST_STATE_DIR:-/data/local/tmp/personal-launcher-volume-fast}
PAYLOAD=${PLV_FAST_PAYLOAD:-/data/local/tmp/personal-launcher-volume-fast.apk}
PROC_ROOT=${PLV_FAST_PROC_ROOT:-/proc}
MAIN=com.sadique.launcher.ShellVolumeBridge
NAME=personal-launcher-volume-fast
PID_FILE="$STATE/pid"
READY="$STATE/ready"
REPEAT_FILE="$STATE/repeat-ms"
PANEL_FILE="$STATE/panel"
LOG="$STATE/bridge.log"

running() {
    [ -r "$PID_FILE" ] || return 1
    pid=$(cat "$PID_FILE")
    case "$pid" in ''|*[!0-9]*) return 1 ;; esac
    [ -r "$PROC_ROOT/$pid/cmdline" ] || return 1
    text=$(tr '\000' ' ' < "$PROC_ROOT/$pid/cmdline")
    case "$text" in "$NAME"|"$NAME "*|*" $MAIN "*) kill -0 "$pid" 2>/dev/null ;; *) return 1 ;; esac
}

stop_fast() {
    if running; then
        kill "$pid" || return 1
        attempts=0
        while running && [ "$attempts" -lt 50 ]; do sleep 0.1; attempts=$((attempts + 1)); done
        if running; then printf 'Fast helper is still stopping.\n'; return 1; fi
    fi
    rm -f "$PID_FILE" "$READY" "$REPEAT_FILE" "$PANEL_FILE"
}

case "${1:-status}" in
    start)
        [ "$(id -u)" = 2000 ] || { printf 'Run this command through adb shell.\n'; exit 1; }
        repeat_ms=${3:-50}
        case "$repeat_ms" in ''|*[!0-9]*) printf 'Repeat wait must be 50..1000 ms.\n'; exit 1 ;; esac
        [ "$repeat_ms" -ge 50 ] && [ "$repeat_ms" -le 1000 ] || { printf 'Repeat wait must be 50..1000 ms.\n'; exit 1; }
        panel=${4:-system}
        case "$panel" in system|launcher) ;; *) printf 'Panel must be system or launcher.\n'; exit 1 ;; esac
        device=${2:-/dev/input/event3}
        [ -r "$device" ] && [ -r "$PAYLOAD" ] || { printf 'Missing readable input device or APK payload.\n'; exit 1; }
        mkdir -p "$STATE" || exit 1
        if running; then
            active_repeat=$(cat "$REPEAT_FILE" 2>/dev/null)
            printf 'Fast helper already running (PID %s), repeat wait %s ms.\n' "$pid" "${active_repeat:-unknown}"
            if [ "$active_repeat" != "$repeat_ms" ]; then printf 'Stop and restart to change repeat wait.\n'; exit 1; fi
            active_panel=$(cat "$PANEL_FILE" 2>/dev/null)
            active_panel=${active_panel:-system}
            if [ "$active_panel" != "$panel" ]; then printf 'Stop and restart to change panel.\n'; exit 1; fi
            printf 'Panel: %s.\n' "$active_panel"
            exit 0
        fi
        old=${PLV_OLD_BRIDGE:-/data/local/tmp/volume-hold-bridge.sh}
        if [ -f "$old" ]; then
            sh "$old" stop || exit 1
            attempts=0
            while [ "$attempts" -lt 30 ]; do
                case "$(sh "$old" status)" in 'Bridge is not running.'*) break ;; esac
                sleep 0.1; attempts=$((attempts + 1))
            done
            [ "$attempts" -lt 30 ] || { printf 'Old bridge is still stopping; fast helper was not started.\n'; exit 1; }
        fi
        chmod 0444 "$PAYLOAD" || exit 1
        rm -f "$READY" "$PID_FILE"
        : > "$LOG"
        printf '%s\n' "$repeat_ms" > "$REPEAT_FILE"
        printf '%s\n' "$panel" > "$PANEL_FILE"
        CLASSPATH="$PAYLOAD" nohup app_process /system/bin --nice-name="$NAME" "$MAIN" "$device" "$READY" "$repeat_ms" "$panel" < /dev/null > "$LOG" 2>&1 &
        printf '%s\n' "$!" > "$PID_FILE"
        attempts=0
        while [ "$attempts" -lt 60 ]; do
            if running && [ -f "$READY" ]; then printf 'Fast helper running (PID %s). Hold 300ms; repeat wait %sms; step 1; panel %s.\n' "$pid" "$repeat_ms" "$panel"; exit 0; fi
            sleep 0.1; attempts=$((attempts + 1))
        done
        printf 'Fast helper failed to become ready.\n'; cat "$LOG"
        if stop_fast; then
            if [ "$panel" = launcher ]; then
                printf 'Keeping fast volume repeats with the stock panel; launcher-panel startup failed.\n'
                sh "$0" start "$device" "$repeat_ms" system
            elif [ -f "$old" ]; then
                repeat_seconds=$(printf '%d.%03d' "$((repeat_ms / 1000))" "$((repeat_ms % 1000))")
                printf 'Restoring shell fallback (hold 300ms, requested wait %sms; command time remains).\n' "$repeat_ms"
                PLV_REPEAT_DELAY=0.3 PLV_REPEAT_INTERVAL="$repeat_seconds" sh "$old" start "$device"
            fi
        fi
        exit 1
        ;;
    stop)
        stop_fast || exit 1
        printf 'Fast helper stopped. Home/Settings configuration is unchanged.\n'
        ;;
    status)
        if running && [ -f "$READY" ]; then printf 'Fast helper running (PID %s), repeat wait %sms.\n' "$pid" "$(cat "$REPEAT_FILE" 2>/dev/null)"
            printf 'Panel: %s.\n' "$(cat "$PANEL_FILE" 2>/dev/null)"
        else printf 'Fast helper is not ready/running.\n'; [ ! -f "$LOG" ] || cat "$LOG"; fi
        ;;
    *) printf 'Usage: sh fast-volume-bridge.sh start [device] [repeat-ms: 50..1000] [system|launcher] | status | stop\n'; exit 1 ;;
esac
