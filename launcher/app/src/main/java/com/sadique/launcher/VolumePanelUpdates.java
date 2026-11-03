package com.sadique.launcher;

/** One pending UI update, always using the newest signal. No backlog during a held key. */
final class VolumePanelUpdates {
    interface Queue { void post(Runnable task); }
    interface Display { void show(long sentAt); }
    private final Queue queue;
    private final Display display;
    private boolean queued, closed;
    private long latest;
    VolumePanelUpdates(Queue queue, Display display) { this.queue = queue; this.display = display; }
    synchronized void offer(long sentAt) {
        if (closed) return;
        latest = sentAt;
        if (queued) return;
        queued = true;
        queue.post(() -> {
            long value;
            synchronized (VolumePanelUpdates.this) {
                if (closed) return;
                queued = false; value = latest;
            }
            display.show(value);
        });
    }
    synchronized void close() { closed = true; }
    static boolean fresh(long sentAt, long now) { return sentAt > 0 && now >= sentAt && now - sentAt <= 1000; }
}
