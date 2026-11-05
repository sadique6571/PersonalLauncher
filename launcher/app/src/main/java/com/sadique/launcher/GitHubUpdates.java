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
    private static final AtomicBoolean NATIVE_PENDING=new AtomicBoolean();
    private static volatile Operation active;
    private static boolean awaitingApproval;
    private static final class Operation {
        volatile WeakReference<Activity> owner;
        volatile boolean manual;
        final UpdateDownloadUi progress = new UpdateDownloadUi();
        Operation(Activity host, boolean manual) { owner = new WeakReference<>(host); this.manual = manual; }
        void follow(Activity host) { owner = new WeakReference<>(host); manual = true; progress.attach(host); }
    }
    private static final long SIX_HOURS=6*60*60*1000L;
    private GitHubUpdates() { }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("github_updates",0); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled",true); }
    static boolean manualActive() { Operation operation=active; return NATIVE_PENDING.get() || (operation!=null && operation.manual); }
    static void enabled(Context c,boolean value) { prefs(c).edit().putBoolean("enabled",value).apply(); }
    static String status(Context c) { return prefs(c).getString("status","Checks GitHub while Home is open"); }
    private static void state(Context c,String value) { prefs(c).edit().putString("status",value).apply(); }
    static File readyFile(Context c) { return new File(c.getFilesDir(),"verified-launcher-update.apk"); }
    static Uri uri(Context c) { return Uri.parse("content://"+c.getPackageName()+".updates/apk"); }
    static void check(Activity host,boolean force) {
        if ((!force && !enabled(host)) || host.isFinishing() || host.isDestroyed() || !host.hasWindowFocus()) return;
        if (force && NATIVE_PENDING.get()) return;
        SharedPreferences p=prefs(host);
        if (!force && !readyFile(host).isFile() && System.currentTimeMillis()<p.getLong("next_check",0)) return;
        if (!BUSY.compareAndSet(false,true)) {
            Operation running = active;
            if (force && running != null) running.follow(host);
            return;
        }
        Context c=host.getApplicationContext();
        Operation operation = new Operation(host, force); active = operation;
        if (force) operation.progress.attach(host);
        WORK.execute(() -> {
            try {
                File ready=readyFile(c);
                if (ready.isFile()) {
                    if (verify(c,ready)) { install(operation,c); return; }
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
                    operation.progress.report("Downloading update", 0);
                    download(release,part,operation.progress);
                    operation.progress.report("Verifying update", 99);
                    if (!verify(c,part)) {
                        prefs(c).edit().putLong("checked_asset",release.id).apply();
                        state(c,"Up to date"); return;
                    }
                    if (ready.exists() && !ready.delete()) throw new IOException("Cannot replace update cache");
                    if (!part.renameTo(ready)) throw new IOException("Cannot commit verified APK");
                    prefs(c).edit().putString("ready_hash",release.sha256).putLong("ready_asset",release.id)
                            .putInt("install_attempts",0).putLong("native_offered",0).putLong("installing_since",0).apply();
                    state(c,"Verified update ready");
                    install(operation,c);
                } finally { part.delete(); }
            } catch(Exception failure) {
                readyFile(c).delete();
                state(c,"Update check unavailable · automatic retry later");
                prefs(c).edit().putLong("next_check",System.currentTimeMillis()+15*60*1000L).apply();
                android.util.Log.w("PersonalUpdates","Update operation unavailable ("+failure.getClass().getSimpleName()+")");
            } finally {
                finish(operation,c); active = null; BUSY.set(false);
            }
        });
    }
    private static void finish(Operation operation, Context c) {
        operation.progress.finish();
        if (operation.manual) MAIN.post(() -> {
            Activity host = operation.owner.get();
            String message = status(c);
            if (host != null && !host.isFinishing() && !host.isDestroyed() && host.hasWindowFocus()
                    && !message.equals("Confirm update in Android installer") && !message.equals("Opening Android installer"))
                Toast.makeText(host,message,Toast.LENGTH_LONG).show();
        });
    }
    /** Resume only an installation the user explicitly requested and approved in Android settings. */
    static void resume(Activity host) {
        if (awaitingApproval && host.getPackageManager().canRequestPackageInstalls() && readyFile(host).isFile()) {
            awaitingApproval=false;
            MAIN.postDelayed(() -> { if (!host.isFinishing() && !host.isDestroyed() && host.hasWindowFocus()) installReady(host); },300);
        }
        Operation operation = active;
        if (operation != null && operation.manual) operation.follow(host);
    }
    static void installReady(Activity host) {
        if (host.isFinishing() || host.isDestroyed() || !host.hasWindowFocus() || NATIVE_PENDING.get()) return;
        if (!readyFile(host).isFile()) { check(host,true); return; }
        if (!BUSY.compareAndSet(false,true)) {
            Operation running = active; if (running != null) running.follow(host);
            return;
        }
        Context c=host.getApplicationContext(); Operation operation = new Operation(host,true);
        active = operation; operation.progress.attach(host);
        WORK.execute(() -> {
            try {
                operation.progress.report("Verifying update",99);
                if (!verify(c,readyFile(c))) { readyFile(c).delete(); state(c,"Already current"); return; }
                prefs(c).edit().putInt("install_attempts",0).apply();
                install(operation,c);
            } catch(Exception failure) { state(c,"Update not installed · retry from Updates"); }
            finally { finish(operation,c); active=null; BUSY.set(false); }
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
    private static void install(Operation operation,Context c) throws Exception {
        WeakReference<Activity> owner=operation.owner; boolean manual=operation.manual;
        SharedPreferences p=prefs(c);
        if (!manual && (!enabled(c) || !idle(owner,c))) { state(c,"Verified update ready · installs when Home is idle"); return; }
        long since=p.getLong("installing_since",0);
        if (System.currentTimeMillis()-since<120_000L && since>0) { state(c,"Update installer already opened"); return; }
        int attempts=p.getInt("install_attempts",0);
        if (!manual && attempts>=2) { state(c,"Update ready · automatic attempts paused; use Install update"); return; }
        String hash=hash(readyFile(c));
        if (!hash.equals(p.getString("ready_hash",""))) throw new IOException("Verified APK changed");
        if (manual) {
            if (!NATIVE_PENDING.compareAndSet(false,true)) return;
            state(c,"Opening Android installer");
            operation.progress.complete(() -> {
                try { openNative(owner,c,true); }
                finally { NATIVE_PENDING.set(false); }
            });
            return;
        }
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
        MAIN.post(() -> openNative(owner,c,false));
    }
    /** Called on main after the manual capsule has been removed. */
    private static void openNative(WeakReference<Activity> owner, Context c, boolean manual) {
        Activity host=owner.get(); SharedPreferences p=prefs(c);
        if (host==null || host.isFinishing() || host.isDestroyed() || !host.hasWindowFocus()
                || (!manual && !enabled(c))) {
            state(c,"Verified update ready · open Updates → Install update"); return;
        }
        long asset=p.getLong("ready_asset",0);
        if (!manual && p.getLong("native_offered",0)==asset) return;
        if (!c.getPackageManager().canRequestPackageInstalls()) {
            state(c,"Update ready · approve Android installation");
            if (manual) try {
                awaitingApproval=true;
                host.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+c.getPackageName())));
            } catch(RuntimeException unavailable) {
                awaitingApproval=false; Toast.makeText(host,"Approve this app in TV settings",Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            host.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri(c),"application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            p.edit().putLong("native_offered",asset).putLong("installing_since",System.currentTimeMillis()).apply();
            state(c,"Confirm update in Android installer");
        } catch(RuntimeException unavailable) { state(c,"Android installer unavailable"); }
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
        Set<String> localSigners=signers(local), archiveSigners=signers(archive);
        if (!localSigners.equals(archiveSigners) || localSigners.isEmpty()) throw new IOException("Wrong update signer");
        return UpdatePackagePolicy.accepts(local.packageName,archive.packageName,code(local),code(archive),localSigners,archiveSigners);
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
    private static void download(UpdateRelease release,File target,UpdateDownloadUi progress) throws Exception {
        HttpURLConnection connection=connection(release.url); long size=0, until=System.currentTimeMillis()+180_000;
        try(InputStream input=connection.getInputStream(); FileOutputStream output=new FileOutputStream(target)) {
            byte[] buffer=new byte[32768]; int n;
            while((n=input.read(buffer))!=-1) {
                size+=n; if(size>release.size || size>UpdateRelease.MAX_APK || System.currentTimeMillis()>until) throw new IOException("APK limit");
                output.write(buffer,0,n);
                progress.report("Downloading update",UpdateProgress.percent(size,release.size,false));
            }
            output.getFD().sync();
        } finally { connection.disconnect(); }
        if(size!=release.size || !release.sha256.equals(hash(target))) throw new IOException("APK integrity mismatch");
    }
}

