package com.sadique.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LandscapeFeedTest {
    private static JSONObject page(long id) throws Exception {
        JSONObject metadata=new JSONObject();
        metadata.put("LicenseShortName",new JSONObject().put("value","Public domain"));
        metadata.put("Copyrighted",new JSONObject().put("value","False"));
        metadata.put("AttributionRequired",new JSONObject().put("value","false"));
        metadata.put("ImageDescription",new JSONObject().put("value","A mountain lake beneath snowy peaks."));
        metadata.put("Artist",new JSONObject().put("value","A photographer"));
        JSONObject info=new JSONObject().put("width",2400).put("height",1600).put("mime","image/jpeg")
                .put("thumburl","https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/Lake.jpg/1920px-Lake.jpg")
                .put("extmetadata",metadata);
        return new JSONObject().put("pageid",id).put("title","File:Mountain lake.jpg")
                .put("imageinfo",new JSONArray().put(info));
    }
    private static JSONObject info(JSONObject page) throws Exception { return page.getJSONArray("imageinfo").getJSONObject(0); }
    private static String response(JSONObject... pages) throws Exception {
        JSONArray array=new JSONArray(); for (JSONObject page:pages) array.put(page);
        return new JSONObject().put("query",new JSONObject().put("pages",array)).toString();
    }
    @Test public void freshSceneUsesItsRealDescriptionAndSkipsSeenIds() throws Exception {
        java.util.List<LandscapeFeed.Scene> scenes=LandscapeFeed.scenes(response(page(1),page(2)),id->id.equals("commons:1"));
        assertEquals(1,scenes.size()); assertEquals("commons:2",scenes.get(0).id);
        assertEquals("A mountain lake beneath snowy peaks.",scenes.get(0).caption);
        assertFalse(scenes.get(0).caption.contains("photographer"));
    }
    @Test public void portraitsSmallImagesAndDrawingsAreNotLandscapePhotos() throws Exception {
        JSONObject portrait=page(1); info(portrait).put("width",1600).put("height",2400);
        JSONObject tiny=page(2); info(tiny).put("width",600).put("height",400);
        JSONObject drawing=page(3).put("title","File:Mountain landscape drawing.jpg");
        JSONObject vector=page(4); info(vector).put("mime","image/svg+xml");
        assertTrue(LandscapeFeed.scenes(response(portrait,tiny,drawing,vector),id->false).isEmpty());
    }
    @Test public void requiredCreditsAreNotSilentlyStripped() throws Exception {
        JSONObject credited=page(1);
        info(credited).getJSONObject("extmetadata").getJSONObject("LicenseShortName").put("value","CC BY-SA 4.0");
        JSONObject required=page(2);
        info(required).getJSONObject("extmetadata").getJSONObject("AttributionRequired").put("value","true");
        assertTrue(LandscapeFeed.scenes(response(credited,required),id->false).isEmpty());
    }
    @Test public void unrelatedSubjectsAndUnsafeDownloadsAreRejected() throws Exception {
        JSONObject unrelated=page(1).put("title","File:Bird portrait by a mountain lake.jpg");
        JSONObject unsafe=page(2); info(unsafe).put("thumburl","https://thumb.wikimedia.org.example.org/lake.jpg");
        assertTrue(LandscapeFeed.scenes(response(unrelated,unsafe),id->false).isEmpty());
        assertFalse(LandscapeFeed.imageUrlAllowed("http://upload.wikimedia.org/lake.jpg"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://upload.wikimedia.org:444/lake.jpg"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://name@upload.wikimedia.org/lake.jpg"));
        assertTrue(LandscapeFeed.imageUrlAllowed("https://upload.wikimedia.org/lake.jpg"));
    }
    @Test public void sourceMarkupCreditLinesAndDimensionsDoNotBecomeTheCaption() {
        assertEquals("Forest & lake",LandscapeFeed.caption("<p>Forest &amp; lake</p><p>Photo by Someone</p>"));
        assertEquals("Mountain lake",LandscapeFeed.caption("Mountain lake 2400 × 1600 Photo by Someone"));
        assertEquals("",LandscapeFeed.caption("Photo by Someone"));
    }
    @Test public void queryIsRandomAndSupportsNewRequestsWithoutAFixedPhotoList() {
        String first=LandscapeFeed.queryUrl(0,1),second=LandscapeFeed.queryUrl(0,2);
        assertTrue(first.startsWith("https://commons.wikimedia.org/w/api.php?"));
        assertTrue(first.contains("gsrsort=random")); assertNotEquals(first,second);
        assertNotEquals(first,LandscapeFeed.queryUrl(1,1));
    }
    @Test(expected=org.json.JSONException.class) public void providerErrorsAreNotReportedAsFreshPhotos() throws Exception {
        LandscapeFeed.scenes("{\"error\":{\"code\":\"ratelimited\"}}",id->false);
    }
}
