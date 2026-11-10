package com.sadique.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Theme-specific quick controls; the same grid is used on Home and over another app. */
final class ControlCenterGrid extends LinearLayout {
    private static final int[] TITLES={R.string.h_wifi,R.string.h_bluetooth,R.string.h_inputs,
            R.string.h_display,R.string.h_sound,R.string.h_screensaver};
    private static final int[] ICONS={R.drawable.ic_wifi,android.R.drawable.stat_sys_data_bluetooth,
            R.drawable.ic_input,R.drawable.ic_picture,R.drawable.ic_volume,R.drawable.ic_picture};
    private static final int[] ACTIONS={HomeDashboard.WIFI,HomeDashboard.BLUETOOTH,HomeDashboard.INPUTS,
            HomeDashboard.DISPLAY,HomeDashboard.SOUND,HomeDashboard.SCREENSAVER};
    private final Activity host;
    private final java.util.function.IntConsumer open;
    private final int[] ids=new int[6];
    private int style=-1;
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean watching;
    private GlassBluetooth bluetooth;
    private View wifiTile;
    private TextView wifiLabel, bluetoothLabel;
    private final android.net.ConnectivityManager.NetworkCallback network=new android.net.ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(android.net.Network n){main.post(()->status());}
        @Override public void onLost(android.net.Network n){main.post(()->status());}
        @Override public void onCapabilitiesChanged(android.net.Network n,android.net.NetworkCapabilities c){main.post(()->status());}
    };
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();
        android.net.ConnectivityManager cm=host.getSystemService(android.net.ConnectivityManager.class);
        if(cm!=null)try{cm.registerDefaultNetworkCallback(network);watching=true;}catch(RuntimeException ignored){}
        bluetooth=new GlassBluetooth(host,this::status);bluetooth.start();status();
    }
    @Override protected void onDetachedFromWindow(){
        if(watching){android.net.ConnectivityManager cm=host.getSystemService(android.net.ConnectivityManager.class);if(cm!=null)cm.unregisterNetworkCallback(network);watching=false;}
        if(bluetooth!=null){bluetooth.stop();bluetooth=null;}main.removeCallbacksAndMessages(null);super.onDetachedFromWindow();
    }
    void status(){
        if(style!=ThemeStyle.GLASS||wifiTile==null)return;
        android.net.ConnectivityManager cm=host.getSystemService(android.net.ConnectivityManager.class);
        android.net.Network active=cm==null?null:cm.getActiveNetwork();
        android.net.NetworkCapabilities caps=active==null?null:cm.getNetworkCapabilities(active);
        boolean online=caps!=null&&caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)&&caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        boolean ethernet=caps!=null&&caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
        wifiLabel.setText((ethernet?"Ethernet":"Wi-Fi")+" • "+(online?"Online":"No Internet"));
        GradientDrawable d=new GradientDrawable();d.setColor(online?0x8014532d:0x947f1d1d);d.setCornerRadius(GlassSettings.dp(host,22));d.setStroke(GlassSettings.dp(host,wifiTile.isFocused()?2:1),online?0xb322c55e:0xccef4444);wifiTile.setBackground(d);
        if(bluetoothLabel!=null&&bluetooth!=null)bluetoothLabel.setText(bluetooth.connectedName());
    }

    ControlCenterGrid(Activity host, java.util.function.IntConsumer open) {
        super(host); this.host=host; this.open=open; setOrientation(VERTICAL);
        for(int i=0;i<ids.length;i++) ids[i]=View.generateViewId();
        refresh();
    }
    void refresh() {
        SharedPreferences prefs=ThemePreferences.get(host);
        int next=ThemeStyle.id(prefs);
        if(style==next) return;
        View focused=findFocus(); int focusedId=focused==null?View.NO_ID:focused.getId();
        style=next; wifiTile=null;wifiLabel=null;bluetoothLabel=null;removeAllViews();
        int[][] layout=ControlCenterLayout.rows(style);
        for(int rowIndex=0;rowIndex<layout.length;rowIndex++) {
            int[] items=layout[rowIndex];
            LinearLayout row=new LinearLayout(host); row.setOrientation(HORIZONTAL);
            row.setBaselineAligned(false);
            for(int col=0;col<items.length;col++) {
                int item=items[col];
                View tile=tile(item,items.length);
                tile.setId(ids[item]);
                tile.setNextFocusLeftId(ids[items[Math.max(0,col-1)]]);
                tile.setNextFocusRightId(ids[items[Math.min(items.length-1,col+1)]]);
                if(rowIndex>0) {
                    int[] above=layout[rowIndex-1];
                    tile.setNextFocusUpId(ids[above[ControlCenterLayout.neighbour(col,items.length,above.length)]]);
                }
                if(rowIndex+1<layout.length) {
                    int[] below=layout[rowIndex+1];
                    tile.setNextFocusDownId(ids[below[ControlCenterLayout.neighbour(col,items.length,below.length)]]);
                }
                LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(0,-1,1);
                int gap=GlassSettings.dp(host,style==ThemeStyle.COBALT?2:4);
                position.setMargins(gap,gap,gap,gap); row.addView(tile,position);
            }
            int height=style==ThemeStyle.CLASSIC?64:ControlCenterLayout.stacked(style)?100:items.length==1?76:88;
            addView(row,new LinearLayout.LayoutParams(-1,GlassSettings.dp(host,height)));
        }
        status();
        if(focusedId!=View.NO_ID) {
            View restored=findViewById(focusedId); if(restored!=null) restored.requestFocus();
        }
    }
    GradientDrawable surface(boolean focused) {
        SharedPreferences prefs=ThemePreferences.get(host);
        int radius=GlassSettings.dp(host,ControlCenterLayout.radius(ThemeStyle.id(prefs)));
        if(ThemeStyle.id(prefs)==ThemeStyle.GLASS){
            GradientDrawable card=GlassOsUi.panel(host,22);card.setStroke(GlassSettings.dp(host,focused?2:1),focused?0xccffffff:(Math.round((TvGlassStyle.edge(prefs)>>>24)*.47f)<<24)|0xffffff);return card;
        }
        if(ThemeStyle.custom(prefs)) return new DemoCardSurface(radius,GlassSettings.dp(host,1),focused,prefs,false);
        GradientDrawable result=GlassSettings.background(host,focused); result.setCornerRadius(radius); return result;
    }
    private View tile(int item,int columns) {
        boolean stacked=ControlCenterLayout.stacked(style);
        LinearLayout tile=new LinearLayout(host);
        tile.setOrientation(stacked?VERTICAL:HORIZONTAL);
        tile.setGravity(stacked?Gravity.CENTER:Gravity.CENTER_VERTICAL);
        int pad=GlassSettings.dp(host,columns==3?7:12); tile.setPadding(pad,pad,pad,pad);
        tile.setFocusable(true); tile.setFocusableInTouchMode(true); tile.setClickable(true);
        tile.setContentDescription(host.getString(TITLES[item])); tile.setTag("control_center_tile");
        tile.setBackground(surface(false));
        tile.setOnFocusChangeListener((v,focus)->{tile.setBackground(surface(focus));status();});
        ImageView icon=new ImageView(host); icon.setImageDrawable(TvSymbols.icon(host,ICONS[item]));
        icon.setTag(R.id.theme_symbol_resource,ICONS[item]); icon.setTag("theme_accent");
        icon.setColorFilter(GlassSettings.themeAccent(host)); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size=GlassSettings.dp(host,columns==3?22:26);
        LinearLayout.LayoutParams image=new LinearLayout.LayoutParams(size,size);
        if(stacked) image.bottomMargin=GlassSettings.dp(host,5); else image.setMarginEnd(GlassSettings.dp(host,8));
        tile.addView(icon,image);
        TextView title=GlassSettings.label(host,host.getString(TITLES[item]),columns==3?11:13,0xffffffff);
        title.setGravity(stacked?Gravity.CENTER:Gravity.START|Gravity.CENTER_VERTICAL);
        if(item==0){wifiTile=tile;wifiLabel=title;}if(item==1)bluetoothLabel=title;
        title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if(style==ThemeStyle.COBALT||style==ThemeStyle.AMBER) title.setTypeface(title.getTypeface(),Typeface.BOLD);
        // Three-column cards stack their contents even in the instrument themes to keep labels readable.
        if(columns==3&&!stacked) {
            tile.setOrientation(VERTICAL); tile.setGravity(Gravity.CENTER); title.setGravity(Gravity.CENTER);
            image.setMarginEnd(0); image.bottomMargin=GlassSettings.dp(host,4); icon.setLayoutParams(image);
        }
        tile.addView(title,new LinearLayout.LayoutParams(stacked||columns==3?-1:0,-2,stacked||columns==3?0:1));
        tile.setOnClickListener(v->{v.requestFocus();open.accept(ACTIONS[item]);});
        return tile;
    }
}
