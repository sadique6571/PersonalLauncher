#!/system/bin/sh
# Optional shell-only injector. The APK is a classpath payload, not installed/replaced.
STATE=${PLV_FAST_STATE_DIR:-/data/local/tmp/personal-launcher-volume-fast}
PAYLOAD=${PLV_FAST_PAYLOAD:-/data/local/tmp/personal-launcher-volume-fast.apk}
PROC_ROOT=${PLV_FAST_PROC_ROOT:-/proc}
MAIN=com.sadique.launcher.ShellVolumeBridge
NAME=personal-launcher-volume-fast
PID_FILE="$STATE/pid"
READY="$STATE/ready"
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
    rm -f "$PID_FILE" "$READY"
}

case "${1:-status}" in
    start)
        [ "$(id -u)" = 2000 ] || { printf 'Run this command through adb shell.\n'; exit 1; }
        device=${2:-/dev/input/event3}
        [ -r "$device" ] && [ -r "$PAYLOAD" ] || { printf 'Missing readable input device or APK payload.\n'; exit 1; }
        mkdir -p "$STATE" || exit 1
        if running; then printf 'Fast helper already running (PID %s).\n' "$pid"; exit 0; fi
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
        CLASSPATH="$PAYLOAD" nohup app_process /system/bin --nice-name="$NAME" "$MAIN" "$device" "$READY" < /dev/null > "$LOG" 2>&1 &
        printf '%s\n' "$!" > "$PID_FILE"
        attempts=0
        while [ "$attempts" -lt 60 ]; do
            if running && [ -f "$READY" ]; then printf 'Fast helper running (PID %s). Hold 300ms; repeat wait 10ms; step 1.\n' "$pid"; exit 0; fi
            sleep 0.1; attempts=$((attempts + 1))
        done
        printf 'Fast helper failed to become ready.\n'; cat "$LOG"
        if stop_fast && [ -f "$old" ]; then
            printf 'Restoring shell fallback (hold 300ms, requested wait 10ms; command time remains).\n'
            PLV_REPEAT_DELAY=0.3 PLV_REPEAT_INTERVAL=0.01 sh "$old" start "$device"
        fi
        exit 1
        ;;
    stop)
        stop_fast || exit 1
        printf 'Fast helper stopped. Home/Settings configuration is unchanged.\n'
        ;;
    status)
        if running && [ -f "$READY" ]; then printf 'Fast helper running (PID %s).\n' "$pid"
        else printf 'Fast helper is not ready/running.\n'; [ ! -f "$LOG" ] || cat "$LOG"; fi
        ;;
    *) printf 'Usage: sh fast-volume-bridge.sh start [device] | status | stop\n'; exit 1 ;;
esac
