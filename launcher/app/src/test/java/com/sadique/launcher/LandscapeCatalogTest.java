package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class LandscapeCatalogTest {
    @Test public void rejectIntimateScenesAndKeepOrdinaryPortraits() {
        for(String term:new String[]{"Couple kissing on the beach","Romantic sunset",
                "Intimate couple","NUDE portrait","Wedding KISS","Erotic illustration"})
            assertFalse(term,LandscapeCatalog.familySafe(term));
        assertTrue(LandscapeCatalog.familySafe("Outdoor portrait of a woman"));
        assertTrue(LandscapeCatalog.familySafe("Smiling people hiking in autumn"));
        assertTrue(LandscapeCatalog.familySafe("Mountain at sunrise"));
    }
    @Test public void onlyAllowSupportedHttpsImageHosts() {
        assertTrue(LandscapeCatalog.imageUrlAllowed("https://upload.wikimedia.org/wikipedia/commons/a/aa/photo.jpg"));
        assertTrue(LandscapeCatalog.imageUrlAllowed("https://www.bing.com/th?id=OHR.example"));
        assertFalse(LandscapeCatalog.imageUrlAllowed("http://upload.wikimedia.org/x.jpg"));
        assertFalse(LandscapeCatalog.imageUrlAllowed("https://upload.wikimedia.org.example.org/x.jpg"));
        assertFalse(LandscapeCatalog.imageUrlAllowed("https://user@upload.wikimedia.org/x.jpg"));
    }
    @Test public void bingCatalogExcludesKissingAndRetainsSafePictures() throws Exception {
        String json="{\"images\":["+
                "{\"hsh\":\"x1\",\"title\":\"Mountains\",\"copyright\":\"Nature\",\"url\":\"/th?id=test1\"},"+
                "{\"hsh\":\"x2\",\"title\":\"Couple kissing\",\"copyright\":\"photo\",\"url\":\"/th?id=test2\"}]}";
        java.util.List<LandscapeCatalog.Entry> results=LandscapeCatalog.bing(json);
        assertEquals(1,results.size());
        assertEquals("bing:x1",results.get(0).id);
    }
    @Test public void commonsOnlyAcceptsNonIntimatePublicDomainLandscape() throws Exception {
        String meta="{\"LicenseShortName\":{\"value\":\"Public domain\"},\"ImageDescription\":{\"value\":\"People outdoors\"}}";
        String json="{\"query\":{\"pages\":["+
                "{\"pageid\":11,\"title\":\"File:Family trip.jpg\",\"imageinfo\":[{\"width\":1920,\"height\":1080,"+
                "\"thumburl\":\"https://upload.wikimedia.org/wikipedia/commons/thumb/x.jpg\",\"extmetadata\":"+meta+"}]},"+
                "{\"pageid\":12,\"title\":\"File:Couple kissing.jpg\",\"imageinfo\":[{\"width\":1920,\"height\":1080,"+
                "\"thumburl\":\"https://upload.wikimedia.org/wikipedia/commons/thumb/y.jpg\",\"extmetadata\":"+meta+"}]}" +
                "]}}";
        java.util.List<LandscapeCatalog.Entry> results=LandscapeCatalog.commons(json);
        assertEquals(1,results.size());
        assertEquals("commons:11",results.get(0).id);
    }
}
