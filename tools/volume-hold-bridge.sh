#!/system/bin/sh
# Optional, shell-only volume hold diagnostic. No app permissions, settings or autostart changes.
# sh /data/local/tmp/volume-hold-bridge.sh start [input-device]
# sh /data/local/tmp/volume-hold-bridge.sh status|stop

STATE=${PLV_STATE_DIR:-/data/local/tmp/personal-launcher-volume}
SCRIPT=$(readlink -f "$0") || exit 1
LOCK="$STATE/lock"
PID_FILE="$LOCK/pid"
EVENTS="$STATE/events"
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

repeat_volume() {
    direction=$1
    trap - EXIT
    trap 'exit 0' TERM INT HUP
    # Keep the firmware's initial tap. Add steps only after a held press.
    sleep "${PLV_REPEAT_DELAY:-0.4}"
    deadline=$(( $(date +%s) + ${PLV_MAX_HOLD_SECONDS:-30} ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        cmd media_session volume --stream 3 --adj "$direction" > /dev/null 2>&1 || {
            printf 'AudioService adjustment failed; repeat stopped.\n' >&2
            exit 1
        }
        sleep "${PLV_REPEAT_INTERVAL:-0.15}"
    done
}

watch_events() {
    device=$1
    worker= reader= held=
    stop_repeat() {
        if [ -n "$worker" ]; then
            kill "$worker" 2>/dev/null || :
            wait "$worker" 2>/dev/null || :
        fi
        worker= held=
    }
    cleanup() {
        trap '' TERM INT HUP
        stop_repeat
        if [ -n "$reader" ]; then
            kill "$reader" 2>/dev/null || :
            wait "$reader" 2>/dev/null || :
        fi
        rm -f "$EVENTS" "$PID_FILE"
        rmdir "$LOCK" 2>/dev/null || :
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
    trap cleanup EXIT
    rm -f "$EVENTS"
    mkfifo "$EVENTS" || exit 1
    getevent -lt "$device" > "$EVENTS" 2>> "$LOG" &
    reader=$!
    while IFS= read -r event; do
        case "$event" in
            *EV_KEY*KEY_VOLUMEUP*DOWN) begin_repeat raise ;;
            *EV_KEY*KEY_VOLUMEDOWN*DOWN) begin_repeat lower ;;
            *EV_KEY*KEY_VOLUMEUP*UP) [ "$held" = raise ] && stop_repeat ;;
            *EV_KEY*KEY_VOLUMEDOWN*UP) [ "$held" = lower ] && stop_repeat ;;
            # If the driver supplies native repeats, do not add another repeat loop.
            *EV_KEY*KEY_VOLUMEUP*REPEAT|*EV_KEY*KEY_VOLUMEDOWN*REPEAT) stop_repeat ;;
        esac
    done < "$EVENTS"
}

case "${1:-status}" in
    start)
        device=${2:-/dev/input/event1}
        [ -r "$device" ] || { printf 'Cannot read input device: %s\n' "$device"; exit 1; }
        for program in getevent cmd mkfifo nohup; do
            command -v "$program" > /dev/null 2>&1 || { printf 'Missing command: %s\n' "$program"; exit 1; }
        done
        # Refuse to add repeats without the original key helpers; their disabled mode already repeats.
        helpers=$(settings get secure enabled_accessibility_services)
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
        nohup sh "$SCRIPT" --watch "$device" < /dev/null >> "$LOG" 2>&1 &
        printf '%s\n' "$!" > "$PID_FILE"
        sleep 0.2
        if running; then printf 'Bridge running (PID %s). Native initial tap; held repeat after 400 ms.\n' "$pid"
        else printf 'Bridge failed to start.\n'; cat "$LOG"; exit 1; fi
        ;;
    --watch) watch_events "$2" ;;
    stop)
        if running; then kill "$pid"; printf 'Stop requested. Home/Settings settings are unchanged.\n'
        else printf 'Bridge is not running.\n'; fi
        ;;
    status)
        if running; then printf 'Bridge running (PID %s).\n' "$pid"; else printf 'Bridge is not running.\n'; fi
        ;;
    *) printf 'Usage: sh %s start [input-device] | status | stop\n' "$SCRIPT"; exit 1 ;;
esac
