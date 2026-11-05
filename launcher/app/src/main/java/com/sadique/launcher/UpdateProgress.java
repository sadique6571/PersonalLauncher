package com.sadique.launcher;

/** Download bytes never imply a trusted install: reserve 100 for completed verification. */
final class UpdateProgress {
    private UpdateProgress() { }
    static int percent(long bytes, long total, boolean verified) {
        if (verified) return 100;
        if (total <= 0 || bytes <= 0) return 0;
        return (int) Math.min(99, Math.min(bytes, total) * 100 / total);
    }
}
