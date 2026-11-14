package com.sadique.launcher

/** Fixed local helper deployment; no user-supplied host or shell command. */
object VolumeStartupCommand {
    @JvmStatic
    fun quote(text: String): String = "'" + text.replace("'","'\\''") + "'"
    @JvmStatic
    fun build(scriptBase64: String, sourceApk: String?): String {
        if (!scriptBase64.matches(Regex("[A-Za-z0-9+/=]+"))) throw IllegalArgumentException("Invalid wrapper encoding")
        if (sourceApk == null || !sourceApk.startsWith("/data/app/") || !sourceApk.endsWith(".apk")) throw IllegalArgumentException("Unexpected installed APK path")
        val script = "/data/local/tmp/fast-volume-bridge.sh"
        val next = script + ".new"
        val payload = "/data/local/tmp/personal-launcher-volume-fast.apk"
        return "umask 077; printf %s " + quote(scriptBase64) + " | base64 -d > " + quote(next) +
                " && sh -n " + quote(next) + " && sh " + quote(next) + " stop" +
                " && cp " + quote(sourceApk) + " " + quote(payload + ".new") +
                " && chmod 0444 " + quote(payload + ".new") + " && mv -f " + quote(payload + ".new") + " " + quote(payload) +
                " && mv -f " + quote(next) + " " + quote(script) +
                " && sh " + quote(script) + " start auto 50 launcher && echo PLV_AUTO_START_OK && read -r plv_ack"
    }
}
