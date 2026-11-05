package com.sadique.launcher;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.*;
import android.provider.Settings;
import android.util.Base64;
import io.github.muntashirakon.adb.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Opt-in boot recovery. No root, remote hosts, port scanning or permanent background polling. */
final class VolumeAutoStart {
    private static final String PREFS="volume_auto";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService LIMITS=Executors.newSingleThreadScheduledExecutor();
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final AtomicBoolean HOME_PENDING=new AtomicBoolean();
    private static final AtomicInteger GENERATION=new AtomicInteger();
    private static boolean done;
    private static int attempts;
    private VolumeAutoStart() { }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS,0); }
    static String status(Context c) { return prefs(c).getString("status","Off · one-time pairing required"); }
    private static void state(Context c,String value) { prefs(c).edit().putString("status",value).apply(); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled",false); }
    static boolean paired(Context c) { return prefs(c).getBoolean("paired",false); }
    static boolean busy() { return BUSY.get(); }
    static void requestHome(Context c) {
        HOME_PENDING.set(true);
        request(c,true,true);
    }
    static void disable(Context c) {
        prefs(c).edit().putBoolean("enabled",false).apply(); GENERATION.incrementAndGet();
        state(c,"Auto-start off · current volume helper can keep running");
    }
    static void enable(Context c) {
        if (!paired(c)) { state(c,"Complete one-time pairing first"); return; }
        prefs(c).edit().putBoolean("enabled",true).apply(); request(c,true);
    }
    static void request(Context c) { request(c,false); }
    private static void request(Context c,boolean force) { request(c,force,false); }
    private static synchronized void request(Context source,boolean force,boolean homeOnly) {
        Context c=source.getApplicationContext();
        if ((!enabled(c) && !HomeAutomation.enabled(c)) || !paired(c) || BUSY.get()) return;
        if (homeOnly && !HomeAutomation.enabled(c)) return;
        if (!force && (done || attempts>=2)) return;
        if (!BUSY.compareAndSet(false,true)) return;
        HOME_PENDING.set(false);
        boolean volumeJob=enabled(c) && !homeOnly;
        attempts++; int generation=GENERATION.get();
        phase(c,volumeJob,"Waiting for startup/network readiness");
        MAIN.postDelayed(() -> WORK.execute(() -> {
            try { start(c,generation,volumeJob); synchronized(VolumeAutoStart.class) { done=true; } }
            catch (Exception | LinkageError error) {
                if (generation==GENERATION.get() && (volumeJob || HomeAutomation.enabled(c))) {
                    String stage=volumeJob?status(c):HomeAutomation.status(c);
                    android.util.Log.w("PersonalVolume","Auto-start failed at "+stage+" ("+error.getClass().getSimpleName()+")");
                    phase(c,volumeJob,"Auto-start failed at "+stage+" ("+error.getClass().getSimpleName()+")");
                }
            }
            finally {
                BUSY.set(false);
                if (HOME_PENDING.getAndSet(false) && HomeAutomation.enabled(c)) requestHome(c);
            }
        }),force?0:10000);
    }
    static Bundle setup(Context source,String argument) {
        Context c=source.getApplicationContext();
        HomeAutomation.enabled(c); // Initialize fresh-install consent before pairing changes the migration inputs.
        if (Build.VERSION.SDK_INT<30) throw new IllegalStateException("Local wireless pairing requires Android 11 or newer");
        int port; String code;
        try {
            String[] parts=argument==null?new String[0]:argument.split(":",-1);
            if (parts.length!=2 || !parts[0].matches("[0-9]{1,5}") || !parts[1].matches("[0-9]{6}")) throw new IllegalArgumentException();
            port=Integer.parseInt(parts[0]); if (port<1 || port>65535) throw new IllegalArgumentException(); code=parts[1];
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Use pairing-port:six-digit-code"); }
        if (!BUSY.compareAndSet(false,true)) throw new IllegalStateException("Setup already running; wait and retry");
        int generation=GENERATION.get(); state(c,"Pairing this TV · keep its pairing dialog open");
        WORK.execute(() -> {
            try (Manager manager=new Manager(c); PairingRelay relay=new PairingRelay(port)) {
                if (!manager.pair("127.0.0.1",relay.port(),code)) throw new IOException("Pairing rejected");
                check(c,generation,false);
                prefs(c).edit().putBoolean("paired",true).putBoolean("enabled",true).apply();
                state(c,"Paired · auto-start enabled");
            } catch (Exception | LinkageError error) {
                if (generation==GENERATION.get()) state(c,"Pairing failed · keep the TV pairing dialog open and retry with its current code");
            } finally {
                BUSY.set(false);
                if (generation==GENERATION.get() && enabled(c) && paired(c)) request(c,true);
            }
        });
        Bundle result=new Bundle(); result.putString("status","Pairing started; no pairing code is saved"); return result;
    }
    private static void check(Context c,int generation,boolean requireEnabled) throws IOException {
        if (generation!=GENERATION.get() || (requireEnabled && !enabled(c) && !HomeAutomation.enabled(c))) throw new IOException("Cancelled");
    }
    private static void phase(Context c,boolean volumeJob,String value) {
        if (volumeJob) state(c,value); else HomeAutomation.state(c,value);
    }
    private static void start(Context c,int generation,boolean volumeJob) throws Exception {
        check(c,generation,true);
        boolean grant=c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;
        if (!grant && !HomeAutomation.enabled(c))
            throw new IOException("Existing settings grant is missing");
        ConnectivityManager network=c.getSystemService(ConnectivityManager.class);
        for (int i=0;i<30 && (network==null || network.getActiveNetwork()==null);i++) { check(c,generation,true); Thread.sleep(1000); }
        check(c,generation,true);
        phase(c,volumeJob,"Requesting wireless debugging on");
        // Only request ON. Never repeat the off/on toggle which disconnected this TV.
        if (grant && !Settings.Global.putInt(c.getContentResolver(),"adb_wifi_enabled",1)) throw new IOException("Wireless debugging request refused");
        for (int attempt=0;attempt<2;attempt++) {
            check(c,generation,true);
            try {
                phase(c,volumeJob,"Finding this TV's current connection port");
                java.util.List<Integer> ports=LocalAdbPort.discover(c); check(c,generation,true);
                Exception last=null;
                for (int port:ports) {
                    check(c,generation,true);
                    try (Manager manager=new Manager(c)) {
                        phase(c,volumeJob,"Connecting to a discovered local TLS endpoint");
                        try (PairingRelay tls=new PairingRelay(port,true)) {
                            if (!manager.connect("127.0.0.1",tls.port())) throw new IOException("Local TLS connection failed");
                            check(c,generation,true);
                            if (HomeAutomation.enabled(c)) {
                                HomeAutomation.state(c,"Applying Home and remote setup");
                                try {
                                    check(c,generation,true);
                                    boolean verified=homeShell(manager,tls,HomeSetupCommand.build(c.getPackageName(),android.os.Process.myUid()/100000,Build.VERSION.SDK_INT));
                                    if (HomeAutomation.enabled(c)) HomeAutomation.state(c,verified?"Default Home verified · permissions configured":"System did not confirm Home setup · check the setup guide");
                                } catch (Exception failedHome) {
                                    if (HomeAutomation.enabled(c)) HomeAutomation.state(c,"Home setup failed ("+failedHome.getClass().getSimpleName()+") · retry from setup");
                                    android.util.Log.w("PersonalHomeSetup","Automatic Home setup failed",failedHome);
                                }
                            }
                            if (volumeJob) {
                            state(c,"Starting volume helper");
                            String script;
                            try (InputStream input=c.getAssets().open("fast-volume-bridge.sh")) { script=read(input,32768); }
                            String encoded=Base64.encodeToString(script.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
                            String command=VolumeStartupCommand.build(encoded,c.getApplicationInfo().sourceDir);
                            shell(c,manager,command);
                            state(c,"Started · automatic startup enabled");
                            }
                            return;
                        }
                    } catch (Exception failure) {
                        last=failure;
                        android.util.Log.w("PersonalVolume","Local endpoint failed at "+status(c)+" ("+failure.getClass().getSimpleName()+"); trying next discovered endpoint");
                    }
                }
                if (last!=null) throw last;
                throw new IOException("No usable local TLS endpoint");
            } catch (Exception failure) {
                if (attempt==1) throw failure;
                Thread.sleep(3000);
            }
        }
    }
    /** Only called after APK identity/version/digest verification; does not restart the volume helper. */
    static boolean queueUpdate(Context c,String hash,java.util.function.BooleanSupplier allowed) throws Exception {
        if (!paired(c) || !BUSY.compareAndSet(false,true)) throw new IOException("Recovery busy or pairing unavailable");
        try {
            String command=UpdateInstallCommand.build(c.getPackageName(),android.os.Process.myUid()/100000,hash);
            if (c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED)
                Settings.Global.putInt(c.getContentResolver(),"adb_wifi_enabled",1);
            Exception last=null;
            for (int port:LocalAdbPort.discover(c)) {
                try (Manager manager=new Manager(c); PairingRelay relay=new PairingRelay(port,true)) {
                    if (!manager.connect("127.0.0.1",relay.port())) throw new IOException("Local TLS unavailable");
                    if (!allowed.getAsBoolean()) throw new IOException("Home is no longer idle");
                    ScheduledFuture<?> deadline=LIMITS.schedule(relay::close,25,TimeUnit.SECONDS);
                    try (AdbStream stream=manager.openStream("shell:"+command); InputStream input=stream.openInputStream()) {
                        return UpdateInstallCommand.OK.equals(VolumeStartupResponse.awaitResult(input,32768,UpdateInstallCommand.OK,UpdateInstallCommand.FAILED));
                    } finally { deadline.cancel(false); }
                } catch (Exception failed) { last=failed; }
            }
            if (last!=null) throw last;
            return false;
        } finally {
            BUSY.set(false);
            if (HOME_PENDING.getAndSet(false) && HomeAutomation.enabled(c)) requestHome(c);
        }
    }

    private static boolean homeShell(Manager manager,PairingRelay relay,String command) throws Exception {
        ScheduledFuture<?> deadline=LIMITS.schedule(relay::close,20,TimeUnit.SECONDS);
        try (AdbStream stream=manager.openStream("shell:"+command); InputStream input=stream.openInputStream()) {
            return HomeSetupCommand.MARKER.equals(VolumeStartupResponse.awaitResult(input,32768,HomeSetupCommand.MARKER,HomeSetupCommand.FAILED));
        } finally { deadline.cancel(false); }
    }
    private static void shell(Context c,Manager manager,String command) throws Exception {
        ScheduledFuture<?> deadline=LIMITS.schedule(() -> { try { manager.disconnect(); } catch (IOException ignored) { } },20,TimeUnit.SECONDS);
        state(c,"Opening volume startup shell");
        try (AdbStream stream=manager.openStream("shell:"+command); InputStream input=stream.openInputStream()) {
            state(c,"Waiting for helper readiness confirmation");
            VolumeStartupResponse.awaitReady(input,32768);
            state(c,"Helper confirmed; closing startup channel");
            android.util.Log.d("PersonalVolume","Automatic helper readiness confirmed");
        }
        finally { deadline.cancel(false); }
    }
    private static String read(InputStream input,int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] data=new byte[2048]; int count;
        while ((count=input.read(data))!=-1) { if (out.size()+count>limit) throw new IOException("Response too large"); out.write(data,0,count); }
        return out.toString(StandardCharsets.UTF_8.name());
    }
    private static final class Manager extends AbsAdbConnectionManager {
        private final VolumeAdbIdentity identity;
        Manager(Context c) throws Exception { identity=VolumeAdbIdentity.load(c); setApi(Build.VERSION.SDK_INT); setTimeout(8,TimeUnit.SECONDS); setThrowOnUnauthorised(true); }
        @Override protected PrivateKey getPrivateKey() { return identity.key; }
        @Override protected Certificate getCertificate() { return identity.certificate; }
        @Override protected String getDeviceName() { return "PersonalLauncherVolume"; }
        // The library caches its TLS KeyManager. Keep that identity valid for future sessions.
        @Override public void close() throws IOException { disconnect(); }
    }
}
