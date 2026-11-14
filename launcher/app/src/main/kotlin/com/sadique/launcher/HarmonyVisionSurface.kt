package com.sadique.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable

/** App plates, feature cards and status surfaces for Harmony. */
object HarmonyVisionSurface {
    private val features = arrayOf(
        intArrayOf(0xe9285977.toInt(), 0xd70c2947.toInt()),
        intArrayOf(0xd8545a75.toInt(), 0xc51b244a.toInt()),
        intArrayOf(0xdb675c73.toInt(), 0xc62b2444.toInt())
    )

    private fun dp(context: Context, value: Float): Int {
        return maxOf(1, Math.round(context.resources.displayMetrics.density * value))
    }

    private fun material(ctx: Context, color: Int, app: Boolean): Int {
        val alpha = PanelTransparency.alpha(color ushr 24, ThemePreferences.get(ctx), app)
        return (alpha shl 24) or (color and 0xffffff)
    }

    private fun gradient(ctx: Context, top: Int, bottom: Int, app: Boolean = false): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(material(ctx, top, app), material(ctx, bottom, app)))
    }

    @JvmStatic
    fun feature(ctx: Context, index: Int, focused: Boolean): GradientDrawable {
        val colors = features[Math.floorMod(index, features.size)]
        return gradient(ctx, colors[0], colors[1]).apply {
            cornerRadius = dp(ctx, 24f).toFloat()
            setStroke(dp(ctx, if (focused) 2f else 1f), if (focused) 0xfff7fbff.toInt() else 0x74d2e6ef)
        }
    }

    @JvmStatic
    fun app(ctx: Context, accent: Int, focused: Boolean, radius: Int): GradientDrawable {
        val intensity = TvGlassStyle.intensity(ThemePreferences.get(ctx)) / 100f
        val base = minOf(251, Math.round(155 + intensity * 69))
        val top = MaterialAppearance.mix(0xffd5e8ee.toInt(), accent, .09f)
        val bottom = MaterialAppearance.mix(0xff658fa4.toInt(), accent, .11f)
        val fadeTop = if (focused) minOf(253, base + 28) else base
        val fadeBottom = if (focused) minOf(247, base + 5) else maxOf(105, base - 32)
        return gradient(ctx, (fadeTop shl 24) or (top and 0xffffff),
            (fadeBottom shl 24) or (bottom and 0xffffff), true).apply {
            cornerRadius = radius.toFloat()
            setStroke(dp(ctx, if (focused) 2.2f else 1f), if (focused) 0xfffaffff.toInt() else 0x91e3f2f6.toInt())
        }
    }

    @JvmStatic
    fun control(ctx: Context, focused: Boolean): GradientDrawable {
        val top = if (focused) 0xe95c819c.toInt() else 0xc82a4b64.toInt()
        val bottom = if (focused) 0xe5264767.toInt() else 0xcb132b47.toInt()
        return gradient(ctx, top, bottom).apply {
            cornerRadius = dp(ctx, 25f).toFloat()
            setStroke(dp(ctx, if (focused) 2f else 1f), if (focused) 0xfffaffff.toInt() else 0x779dbdd0)
        }
    }

    @JvmStatic
    fun status(ctx: Context): GradientDrawable = gradient(ctx, 0x9b29485f.toInt(), 0x8b0b2036.toInt()).apply {
        cornerRadius = dp(ctx, 999f).toFloat()
        setStroke(dp(ctx, 1f), 0x65d5e9f5)
    }

    @JvmStatic
    fun applyDockPlate(plate: GradientDrawable, ctx: Context, focused: Boolean) {
        plate.setColor(material(ctx, if (focused) 0xf2202632.toInt() else 0xd9171c24.toInt(), true))
        plate.setStroke(dp(ctx, if (focused) 2.5f else 1f), if (focused) 0xf2ffffff.toInt() else 0x1fffffff)
    }

    @JvmStatic
    fun dock(ctx: Context): GradientDrawable = GradientDrawable().apply {
        setColor(material(ctx, 0xc70e1116.toInt(), true))
        cornerRadius = dp(ctx, 24f).toFloat()
        setStroke(dp(ctx, 1f), 0x1fffffff)
    }

    @JvmStatic
    fun sheet(ctx: Context): GradientDrawable = gradient(ctx, 0xf825415d.toInt(), 0xf90d1c34.toInt()).apply {
        cornerRadius = dp(ctx, 30f).toFloat()
        setStroke(dp(ctx, 1f), 0x99b9d5e6.toInt())
    }
}
