package com.sadique.launcher;

/** Fixed local helper deployment; no user-supplied host or shell command. */
final class VolumeStartupCommand {
    static String quote(String text) { return "'"+text.replace("'","'\\''")+"'"; }
    static String build(String scriptBase64,String sourceApk) {
        if (!scriptBase64.matches("[A-Za-z0-9+/=]+")) throw new IllegalArgumentException("Invalid wrapper encoding");
        if (sourceApk==null || !sourceApk.startsWith("/data/app/") || !sourceApk.endsWith(".apk")) throw new IllegalArgumentException("Unexpected installed APK path");
        String script="/data/local/tmp/fast-volume-bridge.sh", next=script+".new";
        String payload="/data/local/tmp/personal-launcher-volume-fast.apk";
        return "umask 077; printf %s "+quote(scriptBase64)+" | base64 -d > "+quote(next)
                +" && sh -n "+quote(next)+" && sh "+quote(next)+" stop"
                +" && cp "+quote(sourceApk)+" "+quote(payload+".new")
                +" && chmod 0444 "+quote(payload+".new")+" && mv -f "+quote(payload+".new")+" "+quote(payload)
                +" && mv -f "+quote(next)+" "+quote(script)
                +" && sh "+quote(script)+" start auto 50 launcher && echo PLV_AUTO_START_OK";
    }
    private VolumeStartupCommand() { }
}
