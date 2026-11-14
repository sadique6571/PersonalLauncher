package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN for launcher settings. */
object LauncherPin {
    private val worker = Executors.newSingleThreadExecutor()
    private val pinPattern = Regex("[0-9]{4,8}")

    @JvmStatic fun valid(pin: String?): Boolean = pin != null && pinPattern.matches(pin)

    @JvmStatic
    @Throws(Exception::class)
    fun encode(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(salt) + ":" + encoder.encodeToString(derive(pin, salt))
    }

    @JvmStatic
    fun matches(pin: String?, stored: String?): Boolean {
        if (pin == null || !valid(pin) || stored == null) return false
        return try {
            val parts = stored.split(':').dropLastWhile { it.isEmpty() }
            if (parts.size != 2) return false
            val decoder = Base64.getDecoder()
            val salt = decoder.decode(parts[0])
            val expected = decoder.decode(parts[1])
            salt.size == 16 && expected.size == 32 && MessageDigest.isEqual(expected, derive(pin, salt))
        } catch (_: Exception) {
            false
        }
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun prefs(host: Activity) = host.getSharedPreferences("launcher_pin", Context.MODE_PRIVATE)

    @JvmStatic
    fun require(host: Activity, allowed: Runnable): AlertDialog? {
        val prefs = prefs(host)
        val saved = prefs.getString("hash", "")
        if (saved.isNullOrEmpty()) {
            allowed.run()
            return null
        }
        val wait = prefs.getLong("retry_at", 0) - System.currentTimeMillis()
        if (wait in 1..30000) {
            Toast.makeText(host, R.string.h_pin_wait, Toast.LENGTH_LONG).show()
            return null
        }
        return prompt(host, R.string.h_pin_enter) { pin, dialog, input ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            worker.execute {
                val correct = matches(pin, saved)
                host.runOnUiThread {
                    if (host.isDestroyed || !dialog.isShowing) return@runOnUiThread
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    if (correct) {
                        prefs.edit().remove("failures").remove("retry_at").apply()
                        dialog.dismiss()
                        allowed.run()
                    } else {
                        val count = prefs.getInt("failures", 0) + 1
                        val locked = count >= 5
                        prefs.edit().putInt("failures", if (locked) 0 else count)
                            .putLong("retry_at", if (locked) System.currentTimeMillis() + 30000 else 0).apply()
                        if (locked) {
                            dialog.dismiss()
                            Toast.makeText(host, R.string.h_pin_wait, Toast.LENGTH_LONG).show()
                        } else {
                            input.setText("")
                            input.error = host.getString(R.string.h_pin_wrong)
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    fun settings(host: Activity) {
        require(host) {
            val hasPin = !prefs(host).getString("hash", "").isNullOrEmpty()
            val neutral = if (hasPin) R.string.h_pin_remove else android.R.string.cancel
            ThemeDialogBuilder(host).setTitle(R.string.h_local_pin).setMessage(R.string.h_pin_note)
                .setPositiveButton(R.string.h_pin_new) { _, _ -> set(host) }
                .setNeutralButton(neutral) { _, _ -> if (hasPin) prefs(host).edit().clear().apply() }
                .show()
        }
    }

    private fun set(host: Activity) {
        prompt(host, R.string.h_pin_new) firstPrompt@{ first, dialog, input ->
            if (!valid(first)) {
                input.error = host.getString(R.string.h_pin_invalid)
                return@firstPrompt
            }
            dialog.dismiss()
            prompt(host, R.string.h_pin_confirm) confirmPrompt@{ second, confirm, field ->
                if (first != second) {
                    field.error = host.getString(R.string.h_pin_mismatch)
                    return@confirmPrompt
                }
                confirm.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                worker.execute {
                    val encoded = try {
                        encode(second)
                    } catch (_: Exception) {
                        null
                    }
                    host.runOnUiThread {
                        if (host.isDestroyed || !confirm.isShowing) return@runOnUiThread
                        if (encoded != null) {
                            prefs(host).edit().putString("hash", encoded).remove("failures").remove("retry_at").apply()
                            confirm.dismiss()
                        } else {
                            field.error = host.getString(R.string.h_unavailable)
                            confirm.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun prompt(host: Activity, title: Int, submit: (String, AlertDialog, EditText) -> Unit): AlertDialog {
        val input = EditText(host).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(8))
        }
        val dialog = ThemeDialogBuilder(host).setTitle(title).setView(input)
            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                submit(input.text.toString(), dialog, input)
            }
        }
        dialog.show()
        if (ThemeStyle.custom(ThemePreferences.get(host))) GlassSettings.styleDialog(host, dialog)
        return dialog
    }
}
