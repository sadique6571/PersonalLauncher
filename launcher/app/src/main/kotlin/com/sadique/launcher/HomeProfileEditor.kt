package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

internal class HomeProfileEditor(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val muted: Int,
    private val handler: Handler,
    private val notificationOffer: Runnable,
    private val dp: (Int) -> Int,
    private val text: (String, Int, Int) -> TextView,
    private val button: (String, Runnable) -> Button,
    private val avatarSurface: (Boolean, Int) -> GradientDrawable,
    private val avatarOutline: (ImageView) -> Unit,
    private val chooseAvatarShape: Runnable,
    private val avatarBitmap: () -> Bitmap?,
    private val clearAvatar: () -> Unit,
    private val applyAppFont: (TextView) -> Unit,
    private val toolbarSurface: (Boolean) -> GradientDrawable,
    private val profileAsset: (String) -> File,
    private val refreshProfile: () -> Unit,
) {
    private var profile: AlertDialog? = null
    var homeOfferShowing: Boolean = false
        private set

    val isShowing: Boolean get() = profile?.isShowing == true

    fun dismiss() { profile?.dismiss() }


    private var profileAvatarPreview: ImageView? = null

    /** Refreshes the avatar preview inside the profile dialog, if open. */
    fun refreshPreview() {
        val preview = profileAvatarPreview ?: return
        preview.background = avatarSurface(false, 96)
        preview.invalidateOutline()
        val bitmap = avatarBitmap()
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
        refreshPreview()
        val previewRow = LinearLayout(host)
        previewRow.gravity = Gravity.CENTER
        previewRow.setPadding(0, 0, 0, dp(8))
        previewRow.addView(preview, LinearLayout.LayoutParams(dp(96), dp(96)))
        form.addView(previewRow)
        form.addView(button("Photo shape · Round / Rounded square", chooseAvatarShape))
        val name = EditText(host)
        name.isSingleLine = true
        name.hint = "Your name"
        name.setText(prefs.getString("name", "Sadique Sadiq"))
        name.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        name.setTextColor(ThemeStyle.foreground(prefs))
        name.setHintTextColor(ThemeStyle.muted(prefs))
        applyAppFont(name)
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
            clearAvatar()
            refreshProfile()
            refreshPreview()
        }
        val photoBtn = LinearLayout.LayoutParams(0, -2, 1f)
        photoBtn.setMargins(dp(4), dp(8), dp(4), dp(4))
        photoRow.addView(choose, photoBtn)
        val photoBtn2 = LinearLayout.LayoutParams(0, -2, 1f)
        photoBtn2.setMargins(dp(4), dp(8), dp(4), dp(4))
        photoRow.addView(remove, photoBtn2)
        form.addView(photoRow)
        form.addView(text("Profile stays on this device. Google login is not used.", 13, muted))
        // Save on the left, Later on the right (user 2026-11-01).
        val actionRow = LinearLayout(host)
        actionRow.orientation = LinearLayout.HORIZONTAL
        val save = button("Save") {
            val value = PersonalHomeModel.name(name.text.toString())
            if (value.isEmpty()) { name.error = "Enter your name"; return@button }
            prefs.edit().putString("name", value).putBoolean("setup", true).apply()
            refreshProfile()
            profile?.dismiss()
            handler.post { offerDefaultHome() }
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
        dialog.setOnDismissListener { profileAvatarPreview = null; handler.postDelayed(notificationOffer, 1200) }
        dialog.show()
        GlassSettings.styleDialog(host, dialog)
    }

    fun offerDefaultHome() {
        if (host.isDestroyed || host.isFinishing) return
        var alreadyDefault = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = host.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME))
                alreadyDefault = rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        if (homeOfferShowing || (HomeAutomation.enabled(host) && VolumeAutoStart.paired(host))) return
        if (alreadyDefault && VolumeAutoStart.paired(host)) return
        if (!HomeSettingsActivity.available(host) && VolumeAutoStart.paired(host)) return
        // One-time migration: drop the old permanent flag so installs that lost the
        // default-home status on update get the offer again.
        if (prefs.contains("home_offer_done")) prefs.edit().remove("home_offer_done").apply()
        if (System.currentTimeMillis() < prefs.getLong("home_offer_snooze", 0)) return
        homeOfferShowing = true
        ThemeDialogBuilder(host)
            .setTitle("Use as Home screen?")
            .setMessage("Set Personal Launcher as your default launcher so the Home button opens it.")
            .setPositiveButton("Set as default") { _, _ -> LauncherSetupUi.show(host) }
            .setNegativeButton("Later") { _, _ -> prefs.edit().putLong("home_offer_snooze",
                    System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000).apply() }
            .setOnDismissListener { homeOfferShowing = false; handler.postDelayed(notificationOffer, 1200) }
            .show()
    }
}
