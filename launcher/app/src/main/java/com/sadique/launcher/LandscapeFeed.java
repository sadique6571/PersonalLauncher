package com.sadique.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Independent online sources for the preview and Android TV dream. */
final class LandscapeFeed {
    private enum Source { BING, PORTRAITS, COMMONS, OCEAN_EXPLORATION }
    private static final Source[] SOURCES = Source.values();
    private static final String[] MARKETS = {"en-US", "en-GB", "en-AU", "en-CA", "de-DE", "ja-JP"};
    private static final String[] COMMONS_TOPICS = {"mountain", "lake", "waterfall", "coast", "desert", "forest", "beach", "river"};
    private static final int[][] COMMONS_SIZES = {{1920, 1080}, {1280, 720}, {1600, 900}, {2560, 1440}, {3840, 2160}};
    private static final Pattern UNSUITABLE = Pattern.compile(
            "\\b(?:kiss\\w*|couples?|romance|romantic|wedding\\w*|brides?|grooms?|nud\\w*|erotic\\w*|"
            + "sexual\\w*|intimate|lingerie|swimwear|bikinis?|painting\\w*|illustration\\w*|"
            + "nasa|astronaut\\w*|space station|from space|satellite|cira|orbital|artwork\\w*|composites?|people|persons?|families|family|women|woman|children|hikers?|crowds?|portraits?|lovers?|hugging|embracing|group photo)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NATURE = Pattern.compile(
            "\\b(?:mountains?|lakes?|waterfalls?|forests?|parks?|glaciers?|islands?|coasts?|"
            + "canyons?|deserts?|valleys?|rivers?|reefs?|oceans?|cliffs?|dunes?|peaks?|beaches|"
            + "volcano\\w*|waterways?|aurora\\w*|meadows?|wetlands?|landscapes?)\\b", Pattern.CASE_INSENSITIVE);

    static final class Scene {
        final String id, url, subject;
        final boolean reviewed;
        Scene(String id, String url, String subject, boolean reviewed) {
            this.id = id;
            this.url = url;
            this.subject = subject;
            this.reviewed = reviewed;
        }
    }

    private final Random random = new Random();
    private final EnumMap<Source, ArrayDeque<Scene>> queues = new EnumMap<>(Source.class);
    private final EnumMap<Source, Long> retryAfter = new EnumMap<>(Source.class);
    private int nextSource, bingMarket, commonsTopic;

    LandscapeFeed() {
        bingMarket = random.nextInt(MARKETS.length);
        commonsTopic = random.nextInt(COMMONS_TOPICS.length);
        for (Source source : SOURCES) queues.put(source, new ArrayDeque<>());
        addReviewed(Source.PORTRAITS, portraits());
        addReviewed(Source.OCEAN_EXPLORATION, oceanPhotos());
    }

    private void addReviewed(Source source, List<Scene> photos) {
        Collections.shuffle(photos, random);
        queues.get(source).addAll(photos);
    }

    Scene next(Predicate<String> alreadyShown) {
        for (int i = 0; i < SOURCES.length && !Thread.currentThread().isInterrupted(); i++) {
            Source source = SOURCES[nextSource++ % SOURCES.length];
            ArrayDeque<Scene> queue = queues.get(source);
            Scene candidate = unseen(queue, alreadyShown);
            if (candidate != null) return candidate;
            if (source == Source.PORTRAITS || source == Source.OCEAN_EXPLORATION) continue;
            if (System.nanoTime() < retryAfter.getOrDefault(source, Long.MIN_VALUE)) continue;
            try {
                List<Scene> fetched = fetch(source);
                Collections.shuffle(fetched, random);
                queue.addAll(fetched);
                candidate = unseen(queue, alreadyShown);
                retryAfter.put(source, System.nanoTime() + TimeUnit.SECONDS.toNanos(candidate == null ? 5 : 8));
                if (candidate != null) return candidate;
            } catch (Exception unavailable) {
                retryAfter.put(source, System.nanoTime() + TimeUnit.SECONDS.toNanos(45));
            }
        }
        return null;
    }

    private static Scene unseen(ArrayDeque<Scene> queue, Predicate<String> alreadyShown) {
        while (!queue.isEmpty()) {
            Scene item = queue.removeFirst();
            if (imageUrlAllowed(item.url) && !alreadyShown.test(item.id)) return item;
        }
        return null;
    }

    private List<Scene> fetch(Source source) throws Exception {
        switch (source) {
            case BING: return bing(MARKETS[bingMarket++ % MARKETS.length]);
            case COMMONS:
                int topicIndex = commonsTopic++;
                int batch = topicIndex / COMMONS_TOPICS.length;
                return commons(COMMONS_TOPICS[topicIndex % COMMONS_TOPICS.length], batch % 2 == 0,
                        COMMONS_SIZES[(batch / 2) % COMMONS_SIZES.length], (batch / (2 * COMMONS_SIZES.length)) % 8);
            default: throw new AssertionError(source);
        }
    }

    private static List<Scene> bing(String market) throws Exception {
        String host = "https://www.bing.com";
        JSONArray images = json(host + "/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=" + market).getJSONArray("images");
        List<Scene> result = new ArrayList<>();
        for (int i = 0; i < images.length(); i++) {
            JSONObject item = images.getJSONObject(i);
            String base = item.optString("urlbase");
            String subject = item.optString("copyright").split(" \\(©", 2)[0].trim();
            if (!base.startsWith("/th?id=OHR.") || !landscapeSubject(subject, item.optString("title"))) continue;
            String imageId = base.substring("/th?id=OHR.".length()).split("_", 2)[0];
            result.add(new Scene("bing:" + imageId, host + base + "_1920x1080.jpg", subject, false));
        }
        return result;
    }

    private static List<Scene> commons(String topic, boolean cc0, int[] size, int pageIndex) throws Exception {
        String query = "filetype:bitmap filew:" + size[0] + " fileh:" + size[1] + " intitle:" + topic + (cc0 ? " hastemplate:CC-Zero"
                : " hastemplate:PD-USGov-NPS");
        JSONObject response = json("https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2"
                + "&generator=search&gsrnamespace=6&gsrlimit=16&gsroffset=" + pageIndex * 16 + "&gsrsearch=" + encode(query)
                + "&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiurlwidth=1600"
                + "&iiextmetadatafilter=LicenseShortName%7CObjectName%7CImageDescription");
        List<Scene> result = new ArrayList<>();
        JSONObject data = response.optJSONObject("query");
        if (data == null) return result;
        JSONArray pages = data.getJSONArray("pages");
        for (int i = 0; i < pages.length(); i++) {
            JSONObject page = pages.getJSONObject(i);
            JSONObject info = page.getJSONArray("imageinfo").getJSONObject(0);
            JSONObject metadata = info.getJSONObject("extmetadata");
            String license = metadata(metadata, "LicenseShortName");
            String title = page.getString("title").replaceFirst("^File:", "").replaceFirst("(?i)\\.jpe?g$", "");
            String objectName = metadata(metadata, "ObjectName");
            String subject = objectName.isEmpty() ? title : objectName;
            if (!"image/jpeg".equals(info.optString("mime")) || !commonsLicense(license)
                    || !landscapeSubject(title, subject + " " + metadata(metadata, "ImageDescription"))
                    || !fitsWidescreen(info.optInt("width"), info.optInt("height"))) continue;
            String url = fitsWidescreen(info.optInt("thumbwidth"), info.optInt("thumbheight"))
                    ? info.optString("thumburl") : info.optString("url");
            result.add(new Scene("commons:" + page.getLong("pageid"), url, subject, false));
        }
        return result;
    }

    /** Visually reviewed 16:9 source renditions of individual, student and family portraits. */
    private static List<Scene> portraits() {
        List<Scene> result = new ArrayList<>();
        portrait(result, "1758272133786-ee98adcc6837", "Friends together", null);
        portrait(result, "1758687126864-96b61e1b3af0", "Family at home", null);
        portrait(result, "1573894998033-c0cef4ed722b", "School classroom", 0.5);
        portrait(result, "1779896412109-1b221c2b350b", "Friends outdoors", 0.53);
        portrait(result, "1772724317387-7e763804b637", "Friends on a sunny day", 0.48);
        portrait(result, "1682203533347-10b924b3c253", "Reading in the library", 0.5);
        portrait(result, "1741699427799-3fbb70fce948", "Library study time", 0.6);
        portrait(result, "1652666087939-7db7dd87217b", "Baby in the grass", 0.44);
        portrait(result, "1753803806122-5166fbd4d0ed", "Baby in the garden", 0.48);
        portrait(result, "1761839258657-457dda39b5cc", "Family in the garden", 0.49);
        portrait(result, "1762368229295-81f2742fb8a5", "Sunset in Switzerland", 0.55);
        portrait(result, "1770564512661-eb2e142c26fd", "Women outdoors", 0.46);
        portrait(result, "1764740146693-4955d02c98f9", "Outdoor portrait · South Asia", 0.55);
        return result;
    }

    private static void portrait(List<Scene> list, String id, String subject, Double focusY) {
        String framing = focusY == null ? "fit=max" : "fit=crop&crop=focalpoint&fp-x=0.5&fp-y=" + focusY;
        list.add(new Scene("unsplash:" + id, "https://images.unsplash.com/photo-" + id + "?" + framing
                + "&w=1920&h=1080&fm=jpg&q=78", subject, true));
    }

    private static List<Scene> oceanPhotos() {
        List<Scene> result = new ArrayList<>();
        ocean(result, "warty-octopus", "2023/05/ex2103-octopod-1920x1080-1.jpg", "Warty octopus");
        ocean(result, "glass-sponge-wallpaper", "2026/01/ex1606-sponge-1920x1080-1.jpg", "Deep-sea glass sponge");
        ocean(result, "fishy-outcrop", "2022/09/ex2206-outcrop-1920x1080-1.jpg", "Rocky reef");
        ocean(result, "anemones-2", "2023/10/EX2306-anemones-1920x1080-1.jpg", "Deep-sea anemones");
        ocean(result, "coral", "2024/12/ex1606-coral-1920x1080-1.jpg", "Deep-sea coral");
        ocean(result, "chimaera-wallpaper", "2026/01/ex1907-chimaera-1920x1080-1.jpg", "Chimaera");
        return result;
    }

    private static void ocean(List<Scene> list, String id, String path, String subject) {
        list.add(new Scene("noaa:" + id, "https://oceanexplorer.noaa.gov/wp-content/uploads/" + path,
                subject + " · NOAA Ocean Exploration", true));
    }

    private static JSONObject json(String url) throws Exception {
        return new JSONObject(new String(OnlineContent.get(url, 256 * 1024, 3500, 5000), java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String metadata(JSONObject metadata, String key) {
        JSONObject field = metadata.optJSONObject(key);
        return field == null ? "" : field.optString("value").replaceAll("<[^>]*>", " ").trim();
    }

    static boolean landscapeSubject(String title, String description) {
        return !title.trim().isEmpty() && NATURE.matcher(title + " " + description).find()
                && !UNSUITABLE.matcher(title + " " + description).find();
    }

    static boolean commonsLicense(String license) {
        String name = license.trim().toLowerCase(Locale.ROOT);
        return name.equals("public domain") || name.equals("cc0") || name.startsWith("cc0 ");
    }

    static boolean fitsWidescreen(int width, int height) {
        return height > 480 && (long) width * 9 == (long) height * 16;
    }

    static boolean imageUrlAllowed(String url) {
        try {
            URI uri = new URI(url);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            String path = uri.getPath();
            switch (uri.getHost()) {
                case "images.unsplash.com": return path.matches("/photo-[0-9]+-[a-f0-9]+");
                case "www.bing.com": return "/th".equals(path) && uri.getQuery() != null
                        && uri.getQuery().matches("id=OHR\\.[A-Za-z0-9_.-]+_1920x1080\\.jpg");
                case "thumb.wikimedia.org":
                case "upload.wikimedia.org": return path.startsWith("/wikipedia/commons/")
                        && path.toLowerCase(Locale.ROOT).matches(".+\\.jpe?g");
                case "oceanexplorer.noaa.gov": return path.startsWith("/wp-content/uploads/")
                        && path.toLowerCase(Locale.ROOT).endsWith(".jpg");
                default: return false;
            }
        } catch (java.net.URISyntaxException invalid) {
            return false;
        }
    }
}
