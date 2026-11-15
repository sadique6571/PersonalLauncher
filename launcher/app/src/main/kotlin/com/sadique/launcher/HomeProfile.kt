package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.function.IntConsumer

internal class HomeProfile(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val tv: Boolean,
    private val avatar: () -> ImageView?,
    private val userName: () -> TextView?,
    private val surface: (Boolean) -> GradientDrawable,
    private val toolbarSurface: (Boolean) -> GradientDrawable,
    private val text: (String, Int, Int) -> TextView,
    private val chooseSetting: (String, Array<String>, IntConsumer) -> Unit,
    private val profileAsset: (String) -> File,
    private val offerDefaultHome: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    private var profile: AlertDialog? = null
    private var avatarBitmap: Bitmap? = null
    val isShowing: Boolean get() = profile?.isShowing == true

    fun dismiss() {
        profile?.dismiss()
    }

    fun setAvatar(bitmap: Bitmap) {
        avatarBitmap = bitmap
        refreshProfile()
        refreshProfileAvatarPreview()
    }

    private fun dp(value: Float): Int = Math.round(value * host.resources.displayMetrics.density)

    private fun dp(value: Int): Int = dp(value.toFloat())

    fun avatarSurface(focused: Boolean): GradientDrawable {
        return avatarSurface(focused, if (ThemeStyle.custom(prefs) && tv) 52 else 44)
    }
    private fun avatarSurface(focused: Boolean, size: Int): GradientDrawable {
        val shape = surface(focused)
        if (prefs.getInt("avatar_shape", 0) == 0) shape.shape = GradientDrawable.OVAL
        else shape.cornerRadius = dp(HomeGeometry.avatarRadius(size)).toFloat()
        return shape
    }
    fun avatarOutline(photo: ImageView) {
        photo.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (prefs.getInt("avatar_shape", 0) == 0) outline.setOval(0, 0, view.width, view.height)
                else outline.setRoundRect(0, 0, view.width, view.height,
                        HomeGeometry.avatarRadius(Math.min(view.width, view.height)))
            }
        }
        photo.clipToOutline = true
    }
    private fun chooseAvatarShape() {
        chooseSetting("Avatar shape", arrayOf("Round", "Rounded square"), IntConsumer { which ->
            prefs.edit().putInt("avatar_shape", which).apply()
            avatar()?.apply { background = avatarSurface(isFocused); invalidateOutline() }
            refreshProfileAvatarPreview()
        })
    }

    private fun button(label: String, action: Runnable): Button {
        val view = Button(host)
        view.text = label
        view.setTextSize(14f)
        view.setTextColor(ThemeStyle.foreground(prefs))
        view.isAllCaps = false
        view.minHeight = dp(48)
        view.minimumWidth = 0
        view.setPadding(dp(14), dp(8), dp(14), dp(8))
        view.background = surface(false)
        view.setOnFocusChangeListener { v, focus -> v.background = surface(focus) }
        view.setOnClickListener { action.run() }
        return view
    }

    private var profileAvatarPreview: ImageView? = null

    /** Refreshes the avatar preview inside the profile dialog, if open. */
    private fun refreshProfileAvatarPreview() {
        val preview = profileAvatarPreview ?: return
        preview.background = avatarSurface(false, 96)
        preview.invalidateOutline()
        val bitmap = avatarBitmap
        if (bitmap != null) preview.setImageBitmap(bitmap)
        else preview.setImageDrawable(TvSymbols.icon(host, R.drawable.ic_person))
    }

    fun show() {
        val current = profile
        if (current != null && current.isShowing) return
        val form = LinearLayout(host)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(22), dp(10), dp(22), dp(8))
        // Avatar preview inside the dialog (user 2026-11-01).
        val preview = ImageView(host)
        profileAvatarPreview = preview
        preview.scaleType = ImageView.ScaleType.CENTER_CROP
        preview.background = avatarSurface(false, 96)
        avatarOutline(preview)
        preview.contentDescription = "Profile photo"
        refreshProfileAvatarPreview()
        val previewRow = LinearLayout(host)
        previewRow.gravity = Gravity.CENTER
        previewRow.setPadding(0, 0, 0, dp(8))
        previewRow.addView(preview, LinearLayout.LayoutParams(dp(96), dp(96)))
        form.addView(previewRow)
        form.addView(button("Photo shape · Round / Rounded square", this::chooseAvatarShape))
        val name = EditText(host)
        name.isSingleLine = true
        name.hint = "Your name"
        name.setText(prefs.getString("name", "Sadique Sadiq"))
        name.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        name.setTextColor(ThemeStyle.foreground(prefs))
        name.setHintTextColor(ThemeStyle.muted(prefs))
        name.typeface = Typeface.create(ThemeStyle.fontFamily(prefs), Typeface.NORMAL)
        name.setPadding(dp(16), dp(12), dp(16), dp(12))
        name.background = toolbarSurface(false)
        form.addView(name)
        // Choose / Remove side by side (user 2026-11-01).
        val photoRow = LinearLayout(host)
        photoRow.orientation = LinearLayout.HORIZONTAL
        val choose = button("Choose photo") {
            try {
                host.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                        .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 7)
            } catch (unavailable: ActivityNotFoundException) {
                Toast.makeText(host, "No photo picker available on this device", Toast.LENGTH_LONG).show()
            } catch (unavailable: SecurityException) {
                Toast.makeText(host, "No photo picker available on this device", Toast.LENGTH_LONG).show()
            }
        }
        val remove = button("Remove photo") {
            val photo = profileAsset("personal-avatar.png")
            if (photo.exists() && !photo.delete()) {
                Toast.makeText(host, "Could not remove photo", Toast.LENGTH_SHORT).show()
                return@button
            }
            avatarBitmap = null
            refreshProfile()
            refreshProfileAvatarPreview()
        }
        val photoBtn = LinearLayout.LayoutParams(0, -2, 1f)
        photoBtn.setMargins(dp(4), dp(8), dp(4), dp(4))
        photoRow.addView(choose, photoBtn)
        val photoBtn2 = LinearLayout.LayoutParams(0, -2, 1f)
        photoBtn2.setMargins(dp(4), dp(8), dp(4), dp(4))
        photoRow.addView(remove, photoBtn2)
        form.addView(photoRow)
        form.addView(text("Profile stays on this device. Google login is not used.", 13, MUTED))
        // Save on the left, Later on the right (user 2026-11-01).
        val actionRow = LinearLayout(host)
        actionRow.orientation = LinearLayout.HORIZONTAL
        val save = button("Save") {
            val value = PersonalHomeModel.name(name.text.toString())
            if (value.isEmpty()) { name.error = "Enter your name"; return@button }
            prefs.edit().putString("name", value).putBoolean("setup", true).apply()
            refreshProfile()
            profile?.dismiss()
            offerDefaultHome()
        }
        val later = button("Later") { profile?.dismiss() }
        val saveParams = LinearLayout.LayoutParams(0, -2, 1f)
        saveParams.setMargins(dp(4), dp(8), dp(4), 0)
        val laterParams = LinearLayout.LayoutParams(0, -2, 1f)
        laterParams.setMargins(dp(4), dp(8), dp(4), 0)
        actionRow.addView(save, saveParams)
        actionRow.addView(later, laterParams)
        form.addView(actionRow)
        val dialog = ThemeDialogBuilder(host).setTitle(if (prefs.getBoolean("setup", false)) "Your profile" else "Welcome · set up your Home")
                .setView(form).create()
        profile = dialog
        dialog.setOnDismissListener { profileAvatarPreview = null; onDismiss() }
        dialog.show()
        GlassSettings.styleDialog(host, dialog)
    }

    fun refreshProfile() {
        val name = prefs.getString("name", "Sadique Sadiq")
        userName()?.text = if (name.isNullOrEmpty()) "Your name" else name
        val bitmap = avatarBitmap
        val photo = avatar()
        if (bitmap != null) photo?.setImageBitmap(bitmap)
        else photo?.setImageDrawable(TvSymbols.icon(host, R.drawable.ic_person))
    }

    fun loadAvatar() {
        // The bounded local avatar must be ready before Home binds its first frame.
        avatarBitmap = ProfileAssets.load(host, profileAsset("personal-avatar.png"), true)
    }

    private companion object {
        const val MUTED = 0xffa4bcd6.toInt()
    }
}
