package com.sadique.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** Bounded HTTPS reads; no disk photo cache and no credentials or telemetry. */
final class OnlineContent {
    private static boolean allowedHost(String host) {
        return host.equals("images.unsplash.com")
                || host.equals("api.open-meteo.com") || host.equals("geocoding-api.open-meteo.com")
                || host.equals("picsum.photos") || host.equals("fastly.picsum.photos")
                || host.equals("www.bing.com");
    }

    private static boolean imageHost(String host) {
        return host.equals("images.unsplash.com")
                || host.equals("picsum.photos") || host.equals("fastly.picsum.photos")
                || host.equals("www.bing.com");
    }

    static byte[] get(String address, int limit) throws IOException {
        // Follow redirects manually (max 3 hops) so every hop is re-validated
        // against the allowlist — never blindly follow off-host redirects.
        String current = address;
        for (int hop = 0; hop < 3; hop++) {
            URL url = new URL(current);
            String host = url.getHost();
            if (!"https".equals(url.getProtocol()) || !allowedHost(host))
                throw new IOException("Unsupported content source");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(10_000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("User-Agent", "PersonalLauncher/4.4");
            connection.setRequestProperty("Accept", imageHost(host) ? "image/jpeg,image/webp,image/png" : "application/json");
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isEmpty()) throw new IOException("Redirect without Location");
                    current = new URL(url, location).toString();
                    continue;
                }
                if (status != 200) throw new IOException("Content service returned HTTP " + status);
                if (connection.getContentLengthLong() > limit) throw new IOException("Content too large");
                try (InputStream input = connection.getInputStream()) { return readBounded(input, limit); }
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many redirects");
    }

    static byte[] readBounded(InputStream input, int limit) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                if (output.size() + count > limit) throw new IOException("Content too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    static String json(String address) throws IOException {
        return new String(get(address, 128 * 1024), StandardCharsets.UTF_8);
    }
}
