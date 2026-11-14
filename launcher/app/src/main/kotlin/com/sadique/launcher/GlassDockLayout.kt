package com.sadique.launcher


/** Quantized flex weights preserve each app's row while one card grows to 1.98 times its neighbours. */
internal object GlassDockLayout {
    const val SPANS = 360 // Divisible by every supported column count.
    private const val COMPACT_CARD_ASPECT_RATIO = 1.60f

    @JvmStatic
    fun cardHeight(cardWidthDp: Int, screenHeightDp: Int): Int {
        val available = Math.max(88, Math.min(136, (screenHeightDp - 312) / 2))
        return Math.min(available, Math.max(80, Math.round(Math.max(1, cardWidthDp) * .625f)))
    }

    @JvmStatic
    fun iconSide(contentWidthDp: Int, screenHeightDp: Int): Int {
        val heightLimit = Math.max(64, Math.min(128, (screenHeightDp - 300) / 2))
        return Math.max(48, Math.min(heightLimit, Math.round(Math.max(1, contentWidthDp) * .78f)))
    }

    @JvmStatic
    fun compactCellWidth(rowWidth: Int, columns: Int): Int {
        val cols = Math.max(2, Math.min(6, columns))
        val minimumSpan = Math.floor(SPANS / (cols + .98)).toInt()
        return Math.max(1, rowWidth * minimumSpan / SPANS)
    }

    @JvmStatic
    fun wideBodyHeight(contentWidthDp: Int, screenHeightDp: Int): Int {
        val heightLimit = Math.max(48, Math.round(iconSide(contentWidthDp, screenHeightDp) * .95f))
        val heightForWidth = Math.max(48, Math.round(contentWidthDp / COMPACT_CARD_ASPECT_RATIO))
        return Math.min(heightLimit, heightForWidth)
    }

    @JvmStatic
    fun wideBodyWidth(availableWidth: Int, height: Int): Int {
        return Math.max(1, Math.min(availableWidth, Math.round(height * COMPACT_CARD_ASPECT_RATIO)))
    }

    @JvmStatic
    fun captionHeight(textSp: Int, fontScale: Float): Int {
        return Math.max(28, Math.min(64, Math.round(Math.max(12, Math.min(22, textSp)) * Math.max(1f, fontScale) * 1.5f + 8)))
    }

    @JvmStatic
    fun iconTileHeight(contentWidthDp: Int, screenHeightDp: Int, captionHeight: Int): Int {
        return wideBodyHeight(contentWidthDp, screenHeightDp) + captionHeight
    }

    @JvmStatic
    fun reflectionHeight(cardHeight: Int): Int {
        return Math.max(16, Math.min(32, Math.round(cardHeight * .20f)))
    }

    @JvmStatic
    fun dockHeight(cardWidthDp: Int, screenHeightDp: Int): Int {
        val tile = iconTileHeight(cardWidthDp, screenHeightDp, captionHeight(16, 1f))
        return tile + reflectionHeight(tile) + 60
    }

    @JvmStatic
    fun span(position: Int, columns: Int, previous: Int, selected: Int, transition: Float): Int {
        val cols = Math.max(2, Math.min(6, columns))
        val trans = Math.max(0f, Math.min(1f, transition))
        val row = position / cols
        val col = position % cols
        var total = 0.0
        var before = 0.0
        var through = 0.0
        for (i in 0 until cols) {
            val index = row * cols + i
            var weight = 1.0
            if (index == previous) weight += .98 * (1 - trans)
            if (index == selected) weight += .98 * trans
            if (i < col) before += weight
            if (i <= col) through += weight
            total += weight
        }
        return Math.max(1, Math.round(SPANS * through / total).toInt() - Math.round(SPANS * before / total).toInt())
    }
}
