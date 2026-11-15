package com.sadique.launcher

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Test
import org.junit.Assert.*

class OnlineContentTest {
    @Test fun boundedReadsPreserveTheBody() {
        assertArrayEquals(byteArrayOf(1, 2, 3), OnlineContent.readBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3))
    }
    @Test(expected = IOException::class) fun unknownLengthResponsesCannotExceedTheMemoryLimit() {
        OnlineContent.readBounded(ByteArrayInputStream(ByteArray(20)), 10)
    }
    @Test(expected = IOException::class) fun plainHttpIsNotAllowed() {
        OnlineContent.get("http://images.unsplash.com/photo", 1024)
    }
    @Test(expected = IOException::class) fun arbitraryHostsAreNotAllowed() {
        OnlineContent.get("https://images.unsplash.com.example.org/photo", 1024)
    }
}
