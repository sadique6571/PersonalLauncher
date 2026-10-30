package com.sadique.launcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Keeps saved positions across restarts; new apps append and removed packages disappear. */
final class AppOrder {
    static List<String> merge(List<String> installed, List<String> saved) {
        LinkedHashSet<String> remaining = new LinkedHashSet<>(installed), result = new LinkedHashSet<>();
        for (String pkg : saved) if (remaining.remove(pkg)) result.add(pkg);
        result.addAll(remaining);
        return new ArrayList<>(result);
    }
    static List<String> move(List<String> original, String pkg, int destination) {
        List<String> result = new ArrayList<>(original);
        if (!result.remove(pkg)) return result;
        result.add(Math.max(0, Math.min(result.size(), destination)), pkg);
        return result;
    }
}
