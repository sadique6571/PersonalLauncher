package com.sadique.launcher

/** Pure ordering/validation rules for local library data; no Google accounts or private histories. */
object HomeLibraryModel {

    @JvmStatic
    fun title(raw: String?): String {
        if (raw == null) return ""
        val text = raw.trim()
        val count = text.codePointCount(0, text.length)
        return if (count > 80) text.substring(0, text.offsetByCodePoints(0, 80)) else text
    }

    @JvmStatic
    fun allowedLink(uri: String?): Boolean {
        if (uri.isNullOrEmpty() || uri.length > 8192) return false
        return try {
            val parsed = java.net.URI(uri.trim())
            val scheme = parsed.scheme
            ("https".equals(scheme, ignoreCase = true) || "http".equals(scheme, ignoreCase = true)) &&
                    !parsed.host.isNullOrEmpty() && parsed.userInfo == null
        } catch (_: java.net.URISyntaxException) {
            false
        }
    }

    @JvmStatic
    fun visible(ordered: List<String>, hidden: Set<String>, category: Set<String>?): List<String> {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        for (pkg in ordered) {
            if (!hidden.contains(pkg) && (category == null || category.contains(pkg)) && seen.add(pkg)) {
                out.add(pkg)
            }
        }
        return out
    }

    /** 64-bit stable identity, unlike String.hashCode (which readily collides for package names). */
    @JvmStatic
    fun stableId(key: String): Long {
        var hash = -0x340d631b7bdddcebL
        for (element in key) {
            hash = hash xor element.code.toLong()
            hash *= 0x100000001b3L
        }
        return if (hash == Long.MIN_VALUE) 0 else hash
    }
}
