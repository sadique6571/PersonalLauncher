package com.sadique.launcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class OnlineContentTest {
    @Test public void boundedReadsPreserveTheBody() throws Exception {
        assertArrayEquals(new byte[]{1,2,3}, OnlineContent.readBounded(new ByteArrayInputStream(new byte[]{1,2,3}), 3));
    }
    @Test(expected=IOException.class) public void unknownLengthResponsesCannotExceedTheMemoryLimit() throws Exception {
        OnlineContent.readBounded(new ByteArrayInputStream(new byte[20]), 10);
    }
    @Test(expected=IOException.class) public void plainHttpIsNotAllowed() throws Exception {
        OnlineContent.get("http://images.unsplash.com/photo", 1024);
    }
    @Test(expected=IOException.class) public void arbitraryHostsAreNotAllowed() throws Exception {
        OnlineContent.get("https://images.unsplash.com.example.org/photo", 1024);
    }
}
