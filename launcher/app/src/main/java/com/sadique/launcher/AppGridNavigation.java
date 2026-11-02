package com.sadique.launcher;

/** Horizontal keys follow the visible row-major app order, stopping at the list boundaries. */
final class AppGridNavigation {
    private AppGridNavigation() { }
    static int horizontal(int position, int count, boolean right) {
        if (count <= 0 || position < 0 || position >= count) return -1;
        return Math.max(0, Math.min(count - 1, position + (right ? 1 : -1)));
    }
}
