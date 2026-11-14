package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class LandscapeFeedTest {
    @Test public void liveLandscapeFeedsRejectCouplesAndKissingEvenWhenTheTitleLooksSafe() {
        assertTrue(LandscapeFeed.landscapeSubject("North Window", "Arches National Park"));
        assertFalse(LandscapeFeed.landscapeSubject("Grand Canyon from space", "Seen from the ISS"));
        assertFalse(LandscapeFeed.landscapeSubject("The forest", "A couple kissing beside the trees"));
        assertFalse(LandscapeFeed.landscapeSubject("Lake in spring", "A crowd at a wedding"));
        assertFalse(LandscapeFeed.landscapeSubject("Evening", "A candid scene"));
    }

    @Test public void acceptsOnlyExactSixteenByNineAbove480p() {
        assertTrue(LandscapeFeed.fitsWidescreen(1920, 1080));
        assertTrue(LandscapeFeed.fitsWidescreen(1280, 720));
        assertTrue(LandscapeFeed.fitsWidescreen(960, 540));
        assertFalse(LandscapeFeed.fitsWidescreen(848, 477));
        assertFalse(LandscapeFeed.fitsWidescreen(1600, 899));
        assertFalse(LandscapeFeed.fitsWidescreen(1600, 1200));
        assertFalse(LandscapeFeed.fitsWidescreen(608, 1080));
    }

    @Test public void liveCommonsFeedUsesNoAttributionLicenses() {
        assertTrue(LandscapeFeed.commonsLicense("Public domain"));
        assertTrue(LandscapeFeed.commonsLicense("CC0 1.0"));
        assertFalse(LandscapeFeed.commonsLicense("CC BY-SA 4.0"));
    }

    @Test public void wallpaperFeedOnlyUsesItsOwnPhotoPages() {
        String item = "<link>https://wallspapers.org/wallpaper/snowy-bay</link>";
        assertEquals(java.util.Collections.singleton("https://wallspapers.org/wallpaper/snowy-bay"),
                LandscapeFeed.wallpaperLinks(item + item
                        + "<link>https://example.com/wallpaper/snowy-bay</link>"));
    }

    @Test public void liveWallpaperRequiresPublicDomainAndChecksDescription() throws Exception {
        String page = "<script type=\"application/ld+json\">{\"@type\":\"ImageObject\",\"name\":\"Snowy bay\","
                + "\"description\":\"A forest above the shore\",\"copyrightNotice\":\"Public domain\","
                + "\"license\":\"https://creativecommons.org/publicdomain/zero/1.0/\"}</script>"
                + "<a href=\"/download/92cb432c51/fhd\">Full HD</a>";
        LandscapeFeed.Scene scene = LandscapeFeed.wallpaperScene(page);
        assertNotNull(scene);
        assertEquals("wallspapers:92cb432c51", scene.id);
        assertFalse(scene.reviewed);
        assertNull(LandscapeFeed.wallpaperScene(page.replace("A forest", "A couple kissing in a forest")));
        assertNull(LandscapeFeed.wallpaperScene(page.replace("A forest", "A group of visitors near a forest")));
        assertNull(LandscapeFeed.wallpaperScene(page.replace("publicdomain/zero/1.0", "licenses/by/4.0")));
        assertNull(LandscapeFeed.wallpaperScene(page.replace("/fhd", "/phone")));
    }

    @Test public void flickrRssRequiresLicensedWidescreenNature() throws Exception {
        String feed = "<rss xmlns:m='http://search.yahoo.com/mrss/' "
                + "xmlns:c='http://cyber.law.harvard.edu/rss/creativeCommonsRssModule.html'><channel><item>"
                + "<m:title>Wedgemount Lake</m:title><description>A quiet alpine lake</description>"
                + "<m:category>lake mountain canada</m:category>"
                + "<m:content type='image/jpeg' width='1024' height='576' "
                + "url='https://live.staticflickr.com/65535/53147294188_feebe50604_b.jpg'/>"
                + "<c:license>https://creativecommons.org/publicdomain/zero/1.0/deed.en</c:license>"
                + "</item></channel></rss>";
        assertEquals("flickr:53147294188", LandscapeFeed.flickrScenes(utf8(feed)).get(0).id);
        assertTrue(LandscapeFeed.flickrScenes(utf8(feed.replace("576", "640"))).isEmpty());
        assertTrue(LandscapeFeed.flickrScenes(utf8(feed.replace("publicdomain/zero/1.0", "licenses/by/4.0"))).isEmpty());
        assertTrue(LandscapeFeed.flickrScenes(utf8(feed.replace("quiet alpine lake", "couple kissing by the lake"))).isEmpty());
        assertTrue(LandscapeFeed.flickrScenes(utf8(feed.replace("quiet alpine lake", "cosplay in front of a lake"))).isEmpty());
        assertTrue(LandscapeFeed.flickrScenes(utf8("<!DOCTYPE rss SYSTEM 'https://example.com/x'>" + feed)).isEmpty());
    }

    @Test public void flickrPeopleFeedChecksLicenseContentAndPhotoDimensions() throws Exception {
        String feed = "<rss xmlns:m='http://search.yahoo.com/mrss/' "
                + "xmlns:c='http://cyber.law.harvard.edu/rss/creativeCommonsRssModule.html'><channel><item>"
                + "<m:title>Women and men at a market in Pakistan</m:title>"
                + "<description>Shoppers browsing outdoor stalls</description><m:category>pakistan market</m:category>"
                + "<m:content type='image/jpeg' width='1024' height='683' "
                + "url='https://live.staticflickr.com/65535/53147294188_feebe50604_b.jpg'/>"
                + "<c:license>https://creativecommons.org/publicdomain/zero/1.0/deed.en</c:license>"
                + "</item></channel></rss>";
        LandscapeFeed.Scene scene = LandscapeFeed.flickrPeopleScenes(utf8(feed)).get(0);
        assertTrue(scene.livePeople);
        assertFalse(scene.reviewed);
        assertTrue(LandscapeFeed.flickrScenes(utf8(feed)).isEmpty());
        assertTrue(LandscapeFeed.flickrPeopleScenes(utf8(feed.replace("683", "1024"))).isEmpty());
        assertTrue(LandscapeFeed.flickrPeopleScenes(utf8(feed.replace("browsing outdoor stalls", "kissing"))).isEmpty());
        assertTrue(LandscapeFeed.flickrPeopleScenes(utf8(feed.replace("pakistan market", "pakistan market glamour"))).isEmpty());
        assertTrue(LandscapeFeed.flickrPeopleScenes(utf8(feed.replace("publicdomain/zero/1.0", "licenses/by/4.0"))).isEmpty());
        assertTrue(LandscapeFeed.flickrPeopleScenes(utf8(feed.replace("Women and men", "Buildings")
                .replace("Shoppers browsing outdoor stalls", "Architecture"))).isEmpty());
    }

    private static byte[] utf8(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test public void imageRoutesStayOnTheExpectedHttpsHosts() {
        assertTrue(LandscapeFeed.imageUrlAllowed("https://www.bing.com/th?id=OHR.NorthWindow_EN-US1170338293_1920x1080.jpg"));
        assertTrue(LandscapeFeed.imageUrlAllowed("https://images.unsplash.com/photo-1745690720220-24e337e571c7?w=1600"));
        assertTrue(LandscapeFeed.imageUrlAllowed("https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fb/Mountains.JPG/1920px-Mountains.JPG"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://images-assets.nasa.gov/image/iss074e0208848/iss074e0208848~medium.jpg"));
        assertTrue(LandscapeFeed.imageUrlAllowed("https://oceanexplorer.noaa.gov/wp-content/uploads/2023/05/ex2103-octopod-1920x1080-1.jpg"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://oceanexplorer.noaa.gov/unrelated.jpg"));
        assertTrue(LandscapeFeed.imageUrlAllowed("https://wallspapers.org/download/6a69283189/fhd"));
        assertTrue(LandscapeFeed.imageUrlAllowed("https://www.publicdomainpictures.net/pictures/200000/velka/fall-by-the-lake-14767797082J2.jpg"));
        assertTrue(LandscapeFeed.imageUrlAllowed("https://npgallery.nps.gov/GetAsset/29fc4bc2-b103-4b1b-939f-19d4b91ce209/original.jpg"));
        assertTrue(LandscapeFeed.imageUrlAllowed("https://c.pxhere.com/photos/00/95/lake_landscape_reflection_scenic_calm_outdoors_nature_trees-668634.jpg!d"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://wallspapers.org/download/6a69283189/phone"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://c.pxhere.com.evil.example/photos/00/95/lake-668634.jpg!d"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://www.bing.com/HPImageArchive.aspx?format=js"));
        assertFalse(LandscapeFeed.imageUrlAllowed("http://thumb.wikimedia.org/wikipedia/commons/landscape.jpg"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://user@images.unsplash.com/photo-1745690720220-24e337e571c7"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://images-assets.nasa.gov.evil.example/image/id/id~medium.jpg"));
    }
}
