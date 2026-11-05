package com.sadique.launcher;
import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class UpdateReleaseTest {
    private JSONObject asset() throws Exception {
        return new JSONObject().put("name","PersonalLauncher.apk").put("state","uploaded").put("id",123).put("size",1024)
            .put("browser_download_url","https://github.com/sadique6571/PersonalLauncher/releases/download/build-77/PersonalLauncher.apk")
            .put("digest","sha256:"+new String(new char[64]).replace((char)0,'a'));
    }
    private String release(JSONObject asset) throws Exception { return new JSONObject().put("assets",new JSONArray().put(asset)).toString(); }
    private void reject(JSONObject asset) throws Exception {
        try { UpdateRelease.parse(release(asset)); fail("Untrusted asset accepted"); } catch(IllegalArgumentException expected) { }
    }
    @Test public void stableUploadedAssetHasBoundedSizeAndDigest() throws Exception {
        UpdateRelease result=UpdateRelease.parse(release(asset()));
        assertEquals(123,result.id); assertEquals(1024,result.size); assertEquals(64,result.sha256.length());
    }
    @Test public void foreignAndPlaintextDownloadsAreRejected() throws Exception {
        for(String url:new String[]{"http://github.com/sadique6571/PersonalLauncher/releases/download/x/PersonalLauncher.apk",
            "https://evil.example/sadique6571/PersonalLauncher/releases/download/x/PersonalLauncher.apk",
            "https://github.com/other/repo/releases/download/x/PersonalLauncher.apk",
            "https://github.com@evil.example/sadique6571/PersonalLauncher/releases/download/x/PersonalLauncher.apk"})
            reject(asset().put("browser_download_url",url));
    }
    @Test public void absentDigestOversizeAndPendingUploadsAreRejected() throws Exception {
        reject(asset().put("digest","")); reject(asset().put("size",UpdateRelease.MAX_APK+1));
        reject(asset().put("size",0)); reject(asset().put("state","new"));
    }
    @Test public void draftAndPrereleaseAreRejected() throws Exception {
        for(String flag:new String[]{"draft","prerelease"}) {
            JSONObject data=new JSONObject(release(asset())).put(flag,true);
            try { UpdateRelease.parse(data.toString()); fail(); } catch(IllegalArgumentException expected) { }
        }
    }
}
