package com.sadique.launcher

/** Theme paths that may be created or deleted through ThemeProvider. */
object ThemeFiles {
    const val WALLPAPERS = "wallpapers"
    private val image = Regex("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE)

    @JvmStatic
    fun path(segments: List<String?>?): String? {
        if (segments == null) return null
        if (segments.size == 1) {
            val name = segments[0]
            return name?.takeIf { it == Config.FILE_NAME || it == "wallpaper.jpg" || it == "theme_revision" }
        }
        if (segments.size == 2 && segments[0] == WALLPAPERS) {
            val name = segments[1]
            if (collectionImage(name) || name == WallpaperCaptions.FILE_NAME) return "$WALLPAPERS/$name"
        }
        return null
    }

    @JvmStatic
    fun isCollection(segments: List<String?>?): Boolean {
        return segments?.size == 1 && segments[0] == WALLPAPERS
    }

    @JvmStatic
    fun collectionImage(name: String?): Boolean {
        return name != null && !name.startsWith(".") && image.matches(name)
    }
}
