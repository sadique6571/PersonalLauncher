package com.sadique.launcher

/** Pixel dimensions and spacing for launcher grid tiles. */
internal object AppTileGeometry {
    const val FOCUS_SCALE = 1.10f

    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    const val DESIGN_W_DP = 180f
    const val DESIGN_H_DP = 101f

    /**
     * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glass
     * highlight, capped at the design size so 4 columns on a 1080p TV look exactly as before.
     */
    fun tileWidthPx(innerWidthPx: Int, columns: Int, density: Float): Int {
        val design = Math.round(DESIGN_W_DP * density)
        if (innerWidthPx <= 0 || columns <= 0) return design
        val cell = innerWidthPx / columns
        val fit = ((cell - 12 * density) / FOCUS_SCALE).toInt()
        return Math.max(Math.round(80 * density), Math.min(design, fit))
    }

    fun tileHeightPx(tileWidthPx: Int): Int {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP)
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    fun itemHeightPx(tileHeightPx: Int, density: Float): Int {
        return itemHeightPx(tileHeightPx, true, density)
    }

    /** The same for either layout: the plain grid (dock_row false) uses the compact spacing. */
    fun itemHeightPx(tileHeightPx: Int, dockRow: Boolean, density: Float): Int {
        return tileHeightPx + Math.round((if (dockRow) ITEM_EXTRA_DP else COMPACT_ITEM_EXTRA_DP) * density)
    }

    /**
     * dock_row false: 4dp padding above and below, a 4dp gap and a 20dp name label, and 16dp kept under the last
     * row. The plain grid fits more rows on the screen, and with item_app.xml's roomier spacing the tiles came
     * out small with wide gaps between them.
     */
    const val COMPACT_ITEM_EXTRA_DP = 32f
    const val COMPACT_BOTTOM_ROOM_DP = 16f

    /** item_app.xml: 8dp padding above and below, a 7dp gap and the 22dp name label under the tile. */
    const val ITEM_EXTRA_DP = 45f

    /** DockDecoration's offsets above and below the dock row. */
    const val DOCK_EXTRA_DP = 18f

    /** Room kept under the last row rows_below_dock asks for (MainActivity places the dock with the same). */
    const val BOTTOM_ROOM_DP = 30f

    /** Room kept above the dock for the dock title (48sp hero name plus its 40dp gap) when dock_title is on. */
    const val TITLE_ROOM_DP = 104f

    /** dock_row false: room above the first row for its zoom and glass (MainActivity places the grid with it). */
    const val TOP_ROOM_DP = 12f

    /**
     * Tile width that fits the columns across and, with rows_below_dock > 0, the dock (with its title above it,
     * if shown) plus that many rows down the grid's height; the smaller wins. With rows_below_dock = 0, or before
     * the grid has a height, only the width counts, as before. Without this, 2 or 3 rows below the dock did not
     * fit: the dock was pushed to the top and its title drew over the clock. With dock_row false there is no band
     * or title to make room for: the first row plus rows_below_dock rows fit under a small top margin.
     */
    fun tileWidthPx(innerWidthPx: Int, gridHeightPx: Int, columns: Int, rowsBelowDock: Int, dockTitle: Boolean,
                    dockRow: Boolean, density: Float): Int {
        val w = tileWidthPx(innerWidthPx, columns, density)
        if (gridHeightPx <= 0 || rowsBelowDock <= 0) return w
        val reserved = (if (dockRow) DOCK_EXTRA_DP + BOTTOM_ROOM_DP + (if (dockTitle) TITLE_ROOM_DP else 0f)
            else TOP_ROOM_DP + COMPACT_BOTTOM_ROOM_DP) * density
        val perItem = (gridHeightPx - reserved) / (rowsBelowDock + 1)
        val tileH = perItem - (if (dockRow) ITEM_EXTRA_DP else COMPACT_ITEM_EXTRA_DP) * density
        val byHeight = Math.round(tileH * DESIGN_W_DP / DESIGN_H_DP)
        // Shrink at most to MIN_HEIGHT_FIT of the width-only size. Below that the tiles look lost in their wide
        // columns (2 rows under a titled dock came out at 80dp); the rows that still don't fit run off the bottom
        // and scroll, and MainActivity keeps the title's room above the dock.
        return Math.max(Math.max(Math.round(80 * density), Math.round(w * MIN_HEIGHT_FIT)), Math.min(w, byHeight))
    }

    /** The smallest share of the width-only tile size that fitting rows_below_dock into the height may go to. */
    const val MIN_HEIGHT_FIT = 0.85f

    /** Corner radius for a tile of this width: 16dp at the design width, proportionally less when smaller. */
    fun cornerRadius(tileWidthPx: Int, density: Float): Float {
        return 16f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density))
    }
}
