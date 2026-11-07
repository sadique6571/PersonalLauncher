package com.sadique.launcher;

/** UI-thread ownership of artwork requests across app scans and theme changes. */
final class ArtworkRequestState {
    private int generation;
    private boolean loading;
    boolean pending(int generation) { return loading && this.generation==generation; }
    void begin(int generation) { this.generation=generation; loading=true; }
    boolean finish(int generation) {
        if (!pending(generation)) return false;
        loading=false;
        return true;
    }
}
