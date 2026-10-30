package com.sadique.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** Bounded HTTPS reads; no disk photo cache and no credentials or telemetry. */
final class OnlineContent {
    static byte[] get(String address, int limit) throws IOException {
        URL url = new URL(address);
        String host = url.getHost();
        if (!"https".equals(url.getProtocol()) || !(host.equals("images.unsplash.com")
                || host.equals("api.open-meteo.com") || host.equals("geocoding-api.open-meteo.com")))
            throw new IOException("Unsupported content source");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(10_000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("User-Agent", "PersonalLauncher/4.2");
        connection.setRequestProperty("Accept", host.equals("images.unsplash.com") ? "image/jpeg,image/webp,image/png" : "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Content service returned HTTP " + status);
            if (connection.getContentLengthLong() > limit) throw new IOException("Content too large");
            try (InputStream input = connection.getInputStream()) { return readBounded(input, limit); }
        } finally { connection.disconnect(); }
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
