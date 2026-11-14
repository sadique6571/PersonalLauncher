package com.sadique.launcher

import java.util.Arrays
import java.util.Collections
import java.util.HashSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLibraryModelTest {
    @Test fun hiddenAppsStayHiddenAcrossCategoryFilters() {
        assertEquals(Arrays.asList("video", "tool"), HomeLibraryModel.visible(Arrays.asList("video", "hidden", "tool", "video"),
            Collections.singleton("hidden"), null))
        assertEquals(Collections.singletonList("video"), HomeLibraryModel.visible(Arrays.asList("video", "hidden", "tool"),
            Collections.singleton("hidden"), HashSet(Arrays.asList("video", "hidden"))))
        assertTrue(HomeLibraryModel.visible(Arrays.asList("video"), Collections.emptySet(), Collections.emptySet()).isEmpty())
    }

    @Test fun savedLinksRejectNonWebSchemesAndMissingHosts() {
        for (link in Arrays.asList("https://example.com/movie?id=2", "http://example.com", " HTTPS://EXAMPLE.COM/x "))
            assertTrue(link, HomeLibraryModel.allowedLink(link))
        for (link in Arrays.asList("javascript:alert(1)", "file:///secret", "content://private", "intent:#Intent;end", "https://", "https://user:pass@example.com", "https://example.com/a b"))
            assertFalse(link, HomeLibraryModel.allowedLink(link))
        assertFalse(HomeLibraryModel.allowedLink(null))
    }

    @Test fun titlesAreTrimmedAndUnicodeSafe() {
        assertEquals("", HomeLibraryModel.title(null))
        assertEquals("Movie", HomeLibraryModel.title(" Movie "))
        val emoji = "\ud83c\udfac"
        val title = HomeLibraryModel.title(emoji.repeat(90))
        assertEquals(80, title.codePointCount(0, title.length))
        assertTrue(title.endsWith(emoji))
    }

    @Test fun stableIdentitiesAvoidKnownStringHashCollisions() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(HomeLibraryModel.stableId("Aa"), HomeLibraryModel.stableId("BB"))
        assertEquals(HomeLibraryModel.stableId("com.video"), HomeLibraryModel.stableId("com.video"))
        assertNotEquals(Long.MIN_VALUE, HomeLibraryModel.stableId("com.video"))
    }
}
