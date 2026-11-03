#!/system/bin/sh
# Optional, shell-only volume hold diagnostic. No app permissions, settings or autostart changes.
# sh /data/local/tmp/volume-hold-bridge.sh start [input-device]
# sh /data/local/tmp/volume-hold-bridge.sh status|stop

case "${1:-}" in
    start-adb|stop-adb|status-adb)
        PLV_TRANSPORT=adb
        export PLV_TRANSPORT
        set -- "${1%-adb}" "${2:-/dev/input/event1}"
        ;;
esac
TRANSPORT=${PLV_TRANSPORT:-local}
if [ "$TRANSPORT" = adb ]; then state_base=${TMPDIR:-.}; else state_base=${TMPDIR:-/data/local/tmp}; fi
STATE=${PLV_STATE_DIR:-$state_base/personal-launcher-volume}
SCRIPT=$(readlink -f "$0") || exit 1
LOCK="$STATE/lock"
PID_FILE="$LOCK/pid"
READER_PID="$STATE/reader-pid"
DEVICE_FILE="$STATE/device"
LOG="$STATE/bridge.log"
PROC_ROOT=${PLV_PROC_ROOT:-/proc}

running() {
    [ -r "$PID_FILE" ] || return 1
    pid=$(cat "$PID_FILE")
    case "$pid" in ''|*[!0-9]*) return 1 ;; esac
    [ -r "$PROC_ROOT/$pid/cmdline" ] || return 1
    command_line=$(tr '\000' ' ' < "$PROC_ROOT/$pid/cmdline")
    case "$command_line" in *" $SCRIPT --watch "*) kill -0 "$pid" 2>/dev/null ;; *) return 1 ;; esac
}

reader_running() {
    [ -r "$READER_PID" ] && [ -r "$DEVICE_FILE" ] || return 1
    reader_pid=$(cat "$READER_PID")
    device=$(cat "$DEVICE_FILE")
    case "$reader_pid" in ''|*[!0-9]*) return 1 ;; esac
    [ -r "$PROC_ROOT/$reader_pid/cmdline" ] || return 1
    reader_command=$(tr '\000' ' ' < "$PROC_ROOT/$reader_pid/cmdline")
    case "$reader_command" in
        *"getevent -lt $device "*|*" $SCRIPT --reader $device "*) kill -0 "$reader_pid" 2>/dev/null ;;
        *) return 1 ;;
    esac
}

repeat_volume() {
    direction=$1
    trap - EXIT
    trap 'exit 0' TERM INT HUP
    # Keep the firmware's initial tap. Add steps only after a held press.
    sleep "${PLV_REPEAT_DELAY:-0.4}"
    deadline=$(( $(date +%s) + ${PLV_MAX_HOLD_SECONDS:-30} ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        adjust_volume "$direction" > /dev/null 2>&1 || {
            printf 'AudioService adjustment failed; repeat stopped.\n' >&2
            exit 1
        }
        sleep "${PLV_REPEAT_INTERVAL:-0.15}"
    done
}

adjust_volume() {
    if [ "$TRANSPORT" = adb ]; then
        adb shell cmd media_session volume --stream 3 --adj "$1"
    else
        cmd media_session volume --stream 3 --adj "$1"
    fi
}

parse_events() {
    worker= held=
    stop_repeat() {
        if [ -n "$worker" ]; then
            kill "$worker" 2>/dev/null || :
            wait "$worker" 2>/dev/null || :
        fi
        worker= held=
    }
    begin_repeat() {
        [ "$held" = "$1" ] && return
        stop_repeat
        held=$1
        repeat_volume "$held" &
        worker=$!
        printf 'held %s\n' "$held"
    }
    trap 'exit 0' TERM INT HUP
    trap stop_repeat EXIT
    printf 'event parser ready\n'
    while IFS= read -r event; do
        case "$event" in
            *EV_KEY*KEY_VOLUMEUP*DOWN*) begin_repeat raise ;;
            *EV_KEY*KEY_VOLUMEDOWN*DOWN*) begin_repeat lower ;;
            *EV_KEY*KEY_VOLUMEUP*UP*) [ "$held" = raise ] && stop_repeat ;;
            *EV_KEY*KEY_VOLUMEDOWN*UP*) [ "$held" = lower ] && stop_repeat ;;
            # If the driver supplies native repeats, do not add another repeat loop.
            *EV_KEY*KEY_VOLUMEUP*REPEAT*|*EV_KEY*KEY_VOLUMEDOWN*REPEAT*) stop_repeat ;;
        esac
    done
}

watch_events() {
    cleanup() {
        trap '' TERM INT HUP
        if reader_running; then kill "$reader_pid" 2>/dev/null || :; fi
        rm -f "$READER_PID" "$DEVICE_FILE" "$PID_FILE"
        rmdir "$LOCK" 2>/dev/null || :
    }
    trap 'exit 0' TERM INT HUP
    trap cleanup EXIT
    # Anonymous pipes work on firmware that denies creation of named FIFO files.
    sh "$SCRIPT" --reader "$1" | sh "$SCRIPT" --parse
}

case "${1:-status}" in
    start)
        device=${2:-/dev/input/event1}
        if [ "$TRANSPORT" = adb ]; then programs='adb nohup'; else programs='getevent cmd nohup'; fi
        for program in $programs; do
            command -v "$program" > /dev/null 2>&1 || { printf 'Missing command: %s\n' "$program"; exit 1; }
        done
        if [ "$TRANSPORT" = adb ]; then
            adb shell test -r "$device" || { printf 'Cannot read TV input device: %s\n' "$device"; exit 1; }
            helpers=$(adb shell settings get secure enabled_accessibility_services)
        else
            [ -r "$device" ] || { printf 'Cannot read input device: %s\n' "$device"; exit 1; }
            helpers=$(settings get secure enabled_accessibility_services)
        fi
        # Refuse to add repeats without the original key helpers; their disabled mode already repeats.
        case "$helpers" in
            *com.sadique.launcher.HomeKeyService*|*com.sadique.launcher/.HomeKeyService*|*com.sadique.launcher.InputKeyService*|*com.sadique.launcher/.InputKeyService*) ;;
            *) printf 'Original launcher key helpers are not enabled; refusing duplicate repeats.\n'; exit 1 ;;
        esac
        mkdir -p "$STATE" || exit 1
        if running; then printf 'Bridge already running (PID %s).\n' "$pid"; exit 0; fi
        # Only our own stale lock is removed; unrelated processes are never killed.
        rm -f "$PID_FILE"
        rmdir "$LOCK" 2>/dev/null || :
        mkdir "$LOCK" || { printf 'Bridge lock unavailable.\n'; exit 1; }
        : > "$LOG"
        printf '%s\n' "$device" > "$DEVICE_FILE"
        nohup sh "$SCRIPT" --watch "$device" < /dev/null >> "$LOG" 2>&1 &
        printf '%s\n' "$!" > "$PID_FILE"
        attempts=0
        while [ "$attempts" -lt 10 ]; do
            if running && reader_running; then break; fi
            sleep 0.1
            attempts=$((attempts + 1))
        done
        if running && reader_running; then printf 'Bridge running (PID %s). Native initial tap; held repeat after 400 ms.\n' "$pid"
        else printf 'Bridge failed to start.\n'; cat "$LOG"; exit 1; fi
        ;;
    --watch) watch_events "$2" ;;
    --reader)
        printf '%s\n' "$$" > "$READER_PID"
        if [ "$TRANSPORT" = adb ]; then exec adb shell -tt getevent -lt "$2"; fi
        exec getevent -lt "$2"
        ;;
    --parse) parse_events ;;
    stop)
        # End the producer first: EOF lets the parser stop its repeat worker and exit cleanly.
        if running && reader_running; then kill "$reader_pid"; printf 'Stop requested. Home/Settings settings are unchanged.\n'
        else printf 'Bridge is not running.\n'; fi
        ;;
    status)
        if running; then printf 'Bridge running (PID %s).\n' "$pid"; else printf 'Bridge is not running.\n'; fi
        ;;
    *) printf 'Usage: sh %s start|start-adb [input-device] | status|status-adb | stop|stop-adb\n' "$SCRIPT"; exit 1 ;;
esac
