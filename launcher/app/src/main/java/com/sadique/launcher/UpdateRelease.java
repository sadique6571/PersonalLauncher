package com.sadique.launcher;

import java.net.URI;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Only uploaded stable APK assets from the fixed project are eligible. */
final class UpdateRelease {
    static final String REPO = "sadique6571/PersonalLauncher";
    static final long MAX_APK = 64L * 1024 * 1024;
    final long id, size;
    final String url, sha256;
    private UpdateRelease(long id, long size, String url, String sha256) {
        this.id=id; this.size=size; this.url=url; this.sha256=sha256;
    }
    static UpdateRelease parse(String json) throws Exception {
        JSONObject release=new JSONObject(json);
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) throw new IllegalArgumentException("Stable releases only");
        JSONArray assets=release.getJSONArray("assets");
        for (int i=0;i<assets.length();i++) {
            JSONObject asset=assets.getJSONObject(i);
            if (!"PersonalLauncher.apk".equals(asset.optString("name"))) continue;
            long id=asset.getLong("id"), size=asset.getLong("size");
            String url=asset.getString("browser_download_url"), digest=asset.optString("digest").toLowerCase(Locale.ROOT);
            URI uri=new URI(url);
            if (id<=0 || size<=0 || size>MAX_APK || !"uploaded".equals(asset.optString("state"))
                    || !"https".equals(uri.getScheme()) || !"github.com".equals(uri.getRawAuthority())
                    || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                    || !uri.getPath().startsWith("/"+REPO+"/releases/download/")
                    || !uri.getPath().endsWith("/PersonalLauncher.apk") || !digest.matches("sha256:[0-9a-f]{64}"))
                throw new IllegalArgumentException("Untrusted release asset");
            return new UpdateRelease(id,size,url,digest.substring(7));
        }
        throw new IllegalArgumentException("Release has no launcher APK");
    }
}
