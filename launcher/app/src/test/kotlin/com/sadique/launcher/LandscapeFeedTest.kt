package com.sadique.launcher

import java.nio.charset.StandardCharsets
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeFeedTest {
    @Test fun liveLandscapeFeedsRejectCouplesAndKissingEvenWhenTheTitleLooksSafe() {
        assertTrue(LandscapeFeed.safeSubject("North Window", "Arches National Park"))
        assertFalse(LandscapeFeed.safeSubject("Grand Canyon from space", "Seen from the ISS"))
        assertFalse(LandscapeFeed.safeSubject("The forest", "A couple kissing beside the trees"))
        assertFalse(LandscapeFeed.safeSubject("Lake in spring", "A crowd at a wedding"))
        assertTrue(LandscapeFeed.safeSubject("Evening", "A candid scene"))
    }

    @Test fun acceptsNearWidescreenAbove480p() {
        assertTrue(LandscapeFeed.fitsWidescreen(1920, 1080))
        assertTrue(LandscapeFeed.fitsWidescreen(1280, 720))
        assertTrue(LandscapeFeed.fitsWidescreen(960, 540))
        assertFalse(LandscapeFeed.fitsWidescreen(848, 477))
        assertTrue(LandscapeFeed.fitsWidescreen(1600, 899))
        assertFalse(LandscapeFeed.fitsWidescreen(1600, 1200))
        assertTrue(LandscapeFeed.fitsWidescreen(1620, 1080))
        assertFalse(LandscapeFeed.fitsWidescreen(608, 1080))
    }

    @Test fun liveCommonsFeedUsesNoAttributionLicenses() {
        assertTrue(LandscapeFeed.commonsLicense("Public domain"))
        assertTrue(LandscapeFeed.commonsLicense("CC0 1.0"))
        assertFalse(LandscapeFeed.commonsLicense("CC BY-SA 4.0"))
    }

    @Test fun wallpaperFeedOnlyUsesItsOwnPhotoPages() {
        val item = "<link>https://wallpapers.org/wallpaper/snowy-bay</link>"
        assertEquals(Collections.singleton("https://wallpapers.org/wallpaper/snowy-bay"),
            LandscapeFeed.wallpaperLinks(item + item +
                "<link>https://example.com/wallpaper/snowy-bay</link>"))
    }

    @Test fun liveWallpaperChecksDescription() {
        val page = "<script type=\"application/ld+json\">{\"@type\":\"ImageObject\",\"name\":\"Snowy bay\"," +
            "\"description\":\"A forest above the shore\",\"copyrightNotice\":\"Public domain\"," +
            "\"license\":\"https://creativecommons.org/publicdomain/zero/1.0/\"}</script>" +
            "<a href=\"/download/92cb432c51/fhd\">Full HD</a>"
        val scene = LandscapeFeed.wallpaperScene(page)
        assertNotNull(scene)
        assertEquals("wallpapers:92cb432c51", scene!!.id)
        assertFalse(scene!!.reviewed)
        assertNull(LandscapeFeed.wallpaperScene(page.replace("A forest", "A couple kissing in a forest")))
        assertNotNull(LandscapeFeed.wallpaperScene(page.replace("A forest", "A group of visitors near a forest")))
        assertNotNull(LandscapeFeed.wallpaperScene(page.replace("publicdomain/zero/1.0", "licenses/by/4.0")))
        assertNull(LandscapeFeed.wallpaperScene(page.replace("/fhd", "/phone")))
    }

    @Test fun flickrRssRequiresWidescreenPhotos() {
        val feed = "<rss xmlns:m='http://search.yahoo.com/mrss/' " +
            "xmlns:c='http://cyber.law.harvard.edu/rss/creativeCommonsRssModule.html'><channel><item>" +
            "<m:title>Wedgemount Lake</m:title><description>A quiet alpine lake</description>" +
            "<m:category>lake mountain canada</m:category>" +
            "<m:content type='image/jpeg' width='1024' height='576' " +
            "url='https://live.staticflickr.com/65535/53147294188_feebe50604_b.jpg'/>" +
            "<c:license>https://creativecommons.org/publicdomain/zero/1.0/deed.en</c:license>" +
            "</item></channel></rss>"
        assertEquals("flickr:53147294188", LandscapeFeed.flickrScenes(utf8(feed))[0].id)
        assertTrue(LandscapeFeed.flickrScenes(utf8(feed.replace("576", "768"))).isEmpty())
        assertEquals("flickr:53147294188", LandscapeFeed.flickrScenes(utf8(feed.replace("publicdomain/zero/1.0", "licenses/by/4.0")))[0].id)
        assertTrue(LandscapeFeed.flickrScenes(utf8(feed.replace("quiet alpine lake", "couple kissing by the lake"))).isEmpty())
        assertEquals("flickr:53147294188", LandscapeFeed.flickrScenes(utf8(feed.replace("quiet alpine lake", "cosplay in front of a lake")))[0].id)
        assertTrue(LandscapeFeed.flickrScenes(utf8("<!DOCTYPE rss SYSTEM 'https://example.com/x'>" + feed)).isEmpty())
    }

    @Test fun imageRoutesStayOnTheExpectedHttpsHosts() {
        assertTrue(LandscapeFeed.imageUrlAllowed("https://www.bing.com/th?id=OHR.NorthWindow_EN-US1170338293_1920x1080.jpg"))
        assertTrue(LandscapeFeed.imageUrlAllowed("https://images.unsplash.com/photo-1745690720220-24e337e571c7?w=1600"))
        assertTrue(LandscapeFeed.imageUrlAllowed("https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fb/Mountains.JPG/1920px-Mountains.JPG"))
        assertFalse(LandscapeFeed.imageUrlAllowed("https://images-assets.nasa.gov/image/iss074e0208848/iss074e0208848~medium.jpg"))
        assertTrue(LandscapeFeed.imageUrlAllowed("https://oceanexplorer.noaa.gov/wp-content/uploads/2023/05/ex2103-octopod-1920x1080-1.jpg"))
        assertFalse(LandscapeFeed.imageUrlAllowed("https://oceanexplorer.noaa.gov/unrelated.jpg"))
        assertTrue(LandscapeFeed.imageUrlAllowed("https://wallpapers.org/download/6a69283189/fhd"))
        assertTrue(LandscapeFeed.imageUrlAllowed("https://www.publicdomainpictures.net/pictures/200000/velka/fall-by-the-lake-14767797082J2.jpg"))
        assertTrue(LandscapeFeed.imageUrlAllowed("https://npgallery.nps.gov/GetAsset/29fc4bc2-b103-4b1b-939f-19d4b91ce209/original.jpg"))
        assertTrue(LandscapeFeed.imageUrlAllowed("https://c.pxhere.com/photos/00/95/lake_landscape_reflection_scenic_calm_outdoors_nature_trees-668634.jpg!d"))
        assertFalse(LandscapeFeed.imageUrlAllowed("https://wallpapers.org/download/6a69283189/phone"))
        assertFalse(LandscapeFeed.imageUrlAllowed("https://c.pxhere.com.evil.example/photos/00/95/lake-668634.jpg!d"))
        assertFalse(LandscapeFeed.imageUrlAllowed("https://www.bing.com/HPImageArchive.aspx?format=js"))
        assertFalse(LandscapeFeed.imageUrlAllowed("http://thumb.wikimedia.org/wikipedia/commons/landscape.jpg"))
        assertFalse(LandscapeFeed.imageUrlAllowed("https://user@images.unsplash.com/photo-1745690720220-24e337e571c7"))
        assertFalse(LandscapeFeed.imageUrlAllowed("https://images-assets.nasa.gov.evil.example/image/id/id~medium.jpg"))
    }

    companion object {
        private fun utf8(value: String): ByteArray {
            return value.toByteArray(StandardCharsets.UTF_8)
        }
    }
}
