package com.sadique.launcher;

/** Ownership guard for the optional shell volume-controller relay. */
final class VolumePanelSession<T> implements AutoCloseable {
    interface Endpoint<T> { T get() throws Exception; void set(T controller) throws Exception; }
    private final Endpoint<T> endpoint;
    private final T original, replacement;
    private boolean installed;
    private boolean closed;

    VolumePanelSession(Endpoint<T> endpoint, T original, T replacement) {
        this.endpoint = endpoint; this.original = original; this.replacement = replacement;
    }
    synchronized void install() throws Exception {
        if (closed) throw new IllegalStateException("Volume panel session already closed");
        if (installed) return;
        if (!original.equals(endpoint.get())) throw new IllegalStateException("Volume controller changed before registration");
        endpoint.set(replacement);
        installed = true;
    }
    @Override public synchronized void close() throws Exception {
        if (!installed) { closed = true; return; }
        // SystemUI may have restarted and registered a new controller. Never overwrite it.
        if (replacement.equals(endpoint.get())) endpoint.set(original);
        installed = false;
        closed = true;
    }
    static int flags(int stream, int flags) {
        // Keep every other flag and every non-media callback, including safe-volume warnings.
        return stream == 3 ? flags & ~1 : flags; // STREAM_MUSIC, FLAG_SHOW_UI
    }
}
