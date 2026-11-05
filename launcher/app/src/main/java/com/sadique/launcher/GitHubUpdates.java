package com.sadique.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground-only, bounded release checks and verified self-update. No permanent service. */
final class GitHubUpdates {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final long SIX_HOURS=6*60*60*1000L;
    private GitHubUpdates() { }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("github_updates",0); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled",true); }
    static void enabled(Context c,boolean value) { prefs(c).edit().putBoolean("enabled",value).apply(); }
    static String status(Context c) { return prefs(c).getString("status","Checks GitHub while Home is open"); }
    private static void state(Context c,String value) { prefs(c).edit().putString("status",value).apply(); }
    static File readyFile(Context c) { return new File(c.getFilesDir(),"verified-launcher-update.apk"); }
    static Uri uri(Context c) { return Uri.parse("content://"+c.getPackageName()+".updates/apk"); }
    static void check(Activity host,boolean force) {
        if ((!force && !enabled(host)) || host.isFinishing() || host.isDestroyed() || !host.hasWindowFocus()) return;
        SharedPreferences p=prefs(host);
        if (!force && !readyFile(host).isFile() && System.currentTimeMillis()<p.getLong("next_check",0)) return;
        if (!BUSY.compareAndSet(false,true)) return;
        Context c=host.getApplicationContext(); WeakReference<Activity> owner=new WeakReference<>(host);
        WORK.execute(() -> {
            try {
                File ready=readyFile(c);
                if (ready.isFile()) {
                    if (verify(c,ready)) { install(owner,c,false); return; }
                    prefs(c).edit().putLong("checked_asset",prefs(c).getLong("ready_asset",0)).apply();
                    ready.delete(); state(c,"Up to date");
                }
                if (!force && System.currentTimeMillis()<prefs(c).getLong("next_check",0)) return;
                state(c,"Checking GitHub");
                UpdateRelease release=UpdateRelease.parse(new String(fetch(
                        "https://api.github.com/repos/"+UpdateRelease.REPO+"/releases/latest",512*1024),java.nio.charset.StandardCharsets.UTF_8));
                prefs(c).edit().putLong("next_check",System.currentTimeMillis()+SIX_HOURS).apply();
                if (release.id==prefs(c).getLong("checked_asset",0)) { state(c,"Up to date"); return; }
                state(c,"Downloading update from GitHub");
                File part=new File(c.getFilesDir(),"launcher-update.part");
                try {
                    download(release,part);
                    if (!verify(c,part)) {
                        prefs(c).edit().putLong("checked_asset",release.id).apply();
                        state(c,"Up to date"); return;
                    }
                    if (ready.exists() && !ready.delete()) throw new IOException("Cannot replace update cache");
                    if (!part.renameTo(ready)) throw new IOException("Cannot commit verified APK");
                    prefs(c).edit().putString("ready_hash",release.sha256).putLong("ready_asset",release.id)
                            .putInt("install_attempts",0).putLong("native_offered",0).putLong("installing_since",0).apply();
                    state(c,"Verified update ready");
                    install(owner,c,false);
                } finally { part.delete(); }
            } catch(Exception failure) {
                readyFile(c).delete();
                state(c,"Update check unavailable · automatic retry later");
                prefs(c).edit().putLong("next_check",System.currentTimeMillis()+15*60*1000L).apply();
                android.util.Log.w("PersonalUpdates","Update operation unavailable ("+failure.getClass().getSimpleName()+")");
            } finally { BUSY.set(false); }
        });
    }
    static void installReady(Activity host) {
        if (!readyFile(host).isFile()) { check(host,true); return; }
        if (!BUSY.compareAndSet(false,true)) return;
        Context c=host.getApplicationContext(); WeakReference<Activity> owner=new WeakReference<>(host);
        WORK.execute(() -> {
            try {
                if (!verify(c,readyFile(c))) { readyFile(c).delete(); state(c,"Already current"); return; }
                prefs(c).edit().putInt("install_attempts",0).putLong("installing_since",0).apply();
                install(owner,c,true);
            } catch(Exception failure) { state(c,"Update not installed · retry from Updates"); }
            finally { BUSY.set(false); }
        });
    }
    private static boolean idle(WeakReference<Activity> owner,Context c) {
        if (Looper.myLooper()!=Looper.getMainLooper()) {
            java.util.concurrent.FutureTask<Boolean> task=new java.util.concurrent.FutureTask<>(() -> idle(owner,c));
            MAIN.post(task);
            try { return task.get(2,java.util.concurrent.TimeUnit.SECONDS); }
            catch(Exception unavailable) { task.cancel(false); return false; }
        }
        Activity host=owner.get();
        AudioManager audio=c.getSystemService(AudioManager.class);
        return host!=null && !host.isFinishing() && !host.isDestroyed() && host.hasWindowFocus()
                && !GlassSettings.showing(host) && (audio==null || !audio.isMusicActive());
    }
    private static void install(WeakReference<Activity> owner,Context c,boolean manual) throws Exception {
        if ((!manual && !enabled(c)) || !idle(owner,c)) { state(c,"Verified update ready · installs when Home is idle"); return; }
        SharedPreferences p=prefs(c);
        long since=p.getLong("installing_since",0);
        if (System.currentTimeMillis()-since<120_000L && since>0) return;
        int attempts=p.getInt("install_attempts",0);
        if (!manual && attempts>=2) { state(c,"Update ready · automatic attempts paused; use Install update"); return; }
        String hash=hash(readyFile(c));
        if (!hash.equals(p.getString("ready_hash",""))) throw new IOException("Verified APK changed");
        if (VolumeAutoStart.paired(c) && !VolumeAutoStart.busy()) {
            p.edit().putInt("install_attempts",attempts+1).apply();
            c.grantUriPermission("com.android.shell",uri(c),Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                state(c,"Installing verified update through saved pairing");
                boolean queued=VolumeAutoStart.queueUpdate(c,hash,() -> idle(owner,c) && (manual || enabled(c)));
                if (queued) {
                    p.edit().putLong("installing_since",System.currentTimeMillis()).apply();
                    state(c,"Update installer started"); return;
                }
            } catch(Exception unavailable) { state(c,"Local pairing unavailable · Android installer fallback"); }
            finally { c.revokeUriPermission("com.android.shell",uri(c),Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        } else if (VolumeAutoStart.busy()) { state(c,"Verified update ready · waiting for Home recovery"); return; }
        long asset=p.getLong("ready_asset",0);
        if (!manual && p.getLong("native_offered",0)==asset) return;
        MAIN.post(() -> {
            Activity host=owner.get();
            if (host==null || host.isFinishing() || host.isDestroyed() || !host.hasWindowFocus() || (!manual && !enabled(c))) return;
            if (!c.getPackageManager().canRequestPackageInstalls()) {
                state(c,"Update ready · open Updates → Install update to approve Android installation");
                if (manual) try { host.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+c.getPackageName()))); }
                catch(RuntimeException unavailable) { Toast.makeText(host,"Approve this app in TV settings",Toast.LENGTH_LONG).show(); }
                return;
            }
            try {
                p.edit().putLong("native_offered",asset).apply();
                host.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri(c),"application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                state(c,"Confirm update in Android installer");
            } catch(RuntimeException unavailable) { state(c,"Android installer unavailable"); }
        });
    }
    private static long code(PackageInfo info) { return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode; }
    private static Set<String> signers(PackageInfo info) throws Exception {
        Signature[] signatures=Build.VERSION.SDK_INT>=28 && info.signingInfo!=null?info.signingInfo.getApkContentsSigners():info.signatures;
        Set<String> result=new HashSet<>();
        if (signatures!=null) for(Signature signature:signatures) result.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        return result;
    }
    private static boolean verify(Context c,File apk) throws Exception {
        PackageManager pm=c.getPackageManager();
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo local=pm.getPackageInfo(c.getPackageName(),flags), archive=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags);
        if (archive==null || !c.getPackageName().equals(archive.packageName)) throw new IOException("Wrong update package");
        if (!signers(local).equals(signers(archive)) || signers(local).isEmpty()) throw new IOException("Wrong update signer");
        return UpdatePackagePolicy.accepts(local.packageName,archive.packageName,code(local),code(archive),signers(local),signers(archive));
    }
    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder(); for(byte b:bytes) out.append(String.format(Locale.ROOT,"%02x",b&255)); return out.toString();
    }
    static String hash(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256"); byte[] buffer=new byte[32768]; int n;
        try(InputStream in=new FileInputStream(file)) { while((n=in.read(buffer))!=-1) digest.update(buffer,0,n); }
        return hex(digest.digest());
    }
    private static HttpURLConnection connection(String location) throws Exception {
        for(int redirects=0;redirects<6;redirects++) {
            URL url=new URL(location); String host=url.getHost();
            if (!"https".equals(url.getProtocol()) || url.getUserInfo()!=null || (url.getPort()!=-1 && url.getPort()!=443)
                    || !(host.equals("api.github.com") || host.equals("github.com") || host.equals("release-assets.githubusercontent.com") || host.equals("objects.githubusercontent.com")))
                throw new IOException("Untrusted download host");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent","PersonalLauncher/7.0");
            connection.setRequestProperty("Accept",host.equals("api.github.com")?"application/vnd.github+json":"application/octet-stream");
            int code=connection.getResponseCode();
            if(code==200) return connection;
            String next=connection.getHeaderField("Location"); connection.disconnect();
            if(code>=300 && code<400 && next!=null) { location=new URL(url,next).toString(); continue; }
            throw new IOException("GitHub response "+code);
        }
        throw new IOException("Too many redirects");
    }
    private static byte[] fetch(String location,int limit) throws Exception {
        HttpURLConnection connection=connection(location);
        try(InputStream input=connection.getInputStream(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n;
            while((n=input.read(buffer))!=-1) { if(output.size()+n>limit) throw new IOException("Response limit"); output.write(buffer,0,n); }
            return output.toByteArray();
        } finally { connection.disconnect(); }
    }
    private static void download(UpdateRelease release,File target) throws Exception {
        HttpURLConnection connection=connection(release.url); long size=0, until=System.currentTimeMillis()+180_000;
        try(InputStream input=connection.getInputStream(); FileOutputStream output=new FileOutputStream(target)) {
            byte[] buffer=new byte[32768]; int n;
            while((n=input.read(buffer))!=-1) {
                size+=n; if(size>release.size || size>UpdateRelease.MAX_APK || System.currentTimeMillis()>until) throw new IOException("APK limit");
                output.write(buffer,0,n);
            }
            output.getFD().sync();
        } finally { connection.disconnect(); }
        if(size!=release.size || !release.sha256.equals(hash(target))) throw new IOException("APK integrity mismatch");
    }
}
