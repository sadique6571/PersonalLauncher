package com.sadique.launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.*;
import java.util.*;

/** Read-only bonded devices and public audio profiles; pairing/connection stays in system settings. */
@SuppressLint("MissingPermission") // Every platform read is gated by allowed(); firmware denials are caught.
final class GlassBluetooth {
    static final int PERMISSION=731;
    private final Activity host;
    private final Runnable changed;
    private final BluetoothAdapter adapter;
    private final Map<Integer,BluetoothProfile> profiles=new HashMap<>();
    private boolean running,registered;
    boolean requestingPermission;
    private final BluetoothProfile.ServiceListener services=new BluetoothProfile.ServiceListener(){
        @Override public void onServiceConnected(int type,BluetoothProfile profile){
            if(!running){if(adapter!=null)adapter.closeProfileProxy(type,profile);return;}
            profiles.put(type,profile);changed.run();
        }
        @Override public void onServiceDisconnected(int type){profiles.remove(type);changed.run();}
    };
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){changed.run();}};
    GlassBluetooth(Activity host,Runnable changed){this.host=host;this.changed=changed;BluetoothManager m=host.getSystemService(BluetoothManager.class);adapter=m==null?null:m.getAdapter();}
    private boolean allowed(){return Build.VERSION.SDK_INT<31||host.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    void start(){
        if(running)return;running=true;
        if(adapter==null||!allowed())return;
        try {
            adapter.getProfileProxy(host,services,BluetoothProfile.A2DP);adapter.getProfileProxy(host,services,BluetoothProfile.HEADSET);
            IntentFilter f=new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            if(Build.VERSION.SDK_INT>=33)host.registerReceiver(receiver,f,Context.RECEIVER_EXPORTED);else host.registerReceiver(receiver,f);registered=true;
        }catch(RuntimeException ignored){}
    }
    void stop(){running=false;if(registered){host.unregisterReceiver(receiver);registered=false;}if(adapter!=null)for(Map.Entry<Integer,BluetoothProfile> e:profiles.entrySet())adapter.closeProfileProxy(e.getKey(),e.getValue());profiles.clear();}
    String connectedName(){
        if(!allowed())return "Bluetooth";
        try{for(BluetoothProfile profile:profiles.values())for(BluetoothDevice device:profile.getConnectedDevices()){String name=device.getName();if(name!=null&&!name.isEmpty())return name;}}catch(RuntimeException ignored){}
        return "Bluetooth";
    }
    void show(){
        if(adapter!=null&&!allowed()){requestingPermission=true;host.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},PERMISSION);return;}
        LinearLayout rows=HomeLibraryUi.rows(host);rows.setId(R.id.btDrawer);
        try{
            if(adapter==null)rows.addView(GlassSettings.label(host,"Bluetooth unavailable",13,0xffa5b4cf));
            else {
                Set<BluetoothDevice> bonded=adapter.getBondedDevices();
                if(bonded.isEmpty())rows.addView(GlassSettings.label(host,"No paired devices",13,0xffa5b4cf));
                List<BluetoothDevice> devices=new ArrayList<>(bonded);devices.sort(Comparator.comparing(d->String.valueOf(d.getName()),String.CASE_INSENSITIVE_ORDER));
                for(BluetoothDevice device:devices){
                    boolean connected=false;for(BluetoothProfile p:profiles.values())if(p.getConnectionState(device)==BluetoothProfile.STATE_CONNECTED)connected=true;
                    String name=device.getName();if(name==null||name.isEmpty())name="Bluetooth device";
                    rows.addView(GlassSettings.row(host,name,connected?"Connected • Audio":"Paired • Manage connection",symbol(device),connected?0xff22c55e:GlassSettings.themeAccent(host),this::settings));
                }
            }
        }catch(RuntimeException denied){rows.addView(GlassSettings.label(host,"Device details unavailable. Use Bluetooth settings.",13,0xffa5b4cf));}
        rows.addView(GlassSettings.row(host,"Scan / Pair",null,android.R.drawable.stat_sys_data_bluetooth,GlassSettings.themeAccent(host),this::settings));
        GlassSettings.panel(host,"Bluetooth",rows).show();
    }
    private int symbol(BluetoothDevice d){BluetoothClass c=d.getBluetoothClass();if(c==null)return android.R.drawable.stat_sys_data_bluetooth;
        if(c.getMajorDeviceClass()==BluetoothClass.Device.Major.AUDIO_VIDEO)return R.drawable.ic_volume;
        if(c.getMajorDeviceClass()==BluetoothClass.Device.Major.PERIPHERAL)return R.drawable.ic_input;
        return android.R.drawable.stat_sys_data_bluetooth;
    }
    private void settings(){try{host.startActivity(SystemSettings.bluetoothAccessoriesIntent(host));}catch(RuntimeException unavailable){Toast.makeText(host,"Bluetooth settings unavailable",Toast.LENGTH_SHORT).show();}}
}
