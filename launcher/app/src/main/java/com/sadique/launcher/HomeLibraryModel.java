package com.sadique.launcher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure ordering/validation rules for local library data; no Google accounts or private histories. */
final class HomeLibraryModel {
    private HomeLibraryModel() { }
    static String title(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        int count = text.codePointCount(0, text.length());
        return count > 80 ? text.substring(0, text.offsetByCodePoints(0, 80)) : text;
    }
    static boolean allowedLink(String uri) {
        if (uri == null || uri.length() > 8192) return false;
        try {
            java.net.URI parsed = new java.net.URI(uri.trim());
            String scheme = parsed.getScheme();
            return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && parsed.getHost() != null && !parsed.getHost().isEmpty() && parsed.getUserInfo() == null;
        } catch (java.net.URISyntaxException malformed) { return false; }
    }
    static List<String> visible(List<String> ordered, Set<String> hidden, Set<String> category) {
        List<String> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (String pkg : ordered) if (!hidden.contains(pkg) && (category == null || category.contains(pkg)) && seen.add(pkg)) out.add(pkg);
        return out;
    }
    /** 64-bit stable identity, unlike String.hashCode (which readily collides for package names). */
    static long stableId(String key) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) { hash ^= key.charAt(i); hash *= 0x100000001b3L; }
        return hash == Long.MIN_VALUE ? 0 : hash;
    }
}
