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
    private enum Source { BING, PORTRAITS, COMMONS, NASA }
    private static final Source[] SOURCES = Source.values();
    private static final String[] MARKETS = {"en-US", "en-GB", "en-AU", "en-CA", "de-DE", "ja-JP"};
    private static final String[] COMMONS_TOPICS = {"mountains", "lake", "waterfall", "coast", "desert", "forest"};
    private static final String[] NASA_TOPICS = {"grand canyon", "himalayas", "sahara desert", "patagonia",
            "new zealand coast", "great barrier reef", "iceland glaciers", "amazon river"};
    private static final Pattern UNSUITABLE = Pattern.compile(
            "\\b(?:kiss\\w*|couples?|romance|romantic|wedding\\w*|brides?|grooms?|nud\\w*|erotic\\w*|"
            + "sexual\\w*|intimate|lingerie|swimwear|bikinis?|painting\\w*|illustration\\w*|"
            + "artwork\\w*|composites?|people|crowds?|portraits?|lovers?|hugging|embracing|group photo)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NATURE = Pattern.compile(
            "\\b(?:mountains?|lakes?|waterfalls?|forests?|parks?|glaciers?|islands?|coasts?|"
            + "canyons?|deserts?|valleys?|rivers?|reefs?|oceans?|cliffs?|dunes?|peaks?|beaches|"
            + "volcano\\w*|waterways?|aurora\\w*|meadows?|wetlands?|landscapes?)\\b", Pattern.CASE_INSENSITIVE);

    static final class Scene {
        final String id, url, subject;
        final boolean reviewedPortrait;
        Scene(String id, String url, String subject, boolean reviewedPortrait) {
            this.id = id;
            this.url = url;
            this.subject = subject;
            this.reviewedPortrait = reviewedPortrait;
        }
    }

    private final Random random = new Random();
    private final EnumMap<Source, ArrayDeque<Scene>> queues = new EnumMap<>(Source.class);
    private final EnumMap<Source, Long> retryAfter = new EnumMap<>(Source.class);
    private int nextSource, bingMarket, commonsTopic, nasaTopic, nasaPage;
    private boolean portraitsLoaded;

    LandscapeFeed() {
        bingMarket = random.nextInt(MARKETS.length);
        commonsTopic = random.nextInt(COMMONS_TOPICS.length);
        nasaTopic = random.nextInt(NASA_TOPICS.length);
        for (Source source : SOURCES) queues.put(source, new ArrayDeque<>());
    }

    Scene next(Predicate<String> alreadyShown) {
        for (int i = 0; i < SOURCES.length && !Thread.currentThread().isInterrupted(); i++) {
            Source source = SOURCES[nextSource++ % SOURCES.length];
            ArrayDeque<Scene> queue = queues.get(source);
            Scene candidate = unseen(queue, alreadyShown);
            if (candidate != null) return candidate;
            if (System.nanoTime() < retryAfter.getOrDefault(source, Long.MIN_VALUE)) continue;
            try {
                List<Scene> fetched = fetch(source);
                Collections.shuffle(fetched, random);
                queue.addAll(fetched);
                candidate = unseen(queue, alreadyShown);
                retryAfter.put(source, System.nanoTime() + TimeUnit.SECONDS.toNanos(candidate == null ? 30 : 8));
                if (candidate != null) return candidate;
            } catch (Exception unavailable) {
                retryAfter.put(source, System.nanoTime() + TimeUnit.MINUTES.toNanos(2));
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
            case PORTRAITS:
                if (portraitsLoaded) return new ArrayList<>();
                portraitsLoaded = true;
                return portraits();
            case COMMONS:
                int topicIndex = commonsTopic++;
                return commons(COMMONS_TOPICS[topicIndex % COMMONS_TOPICS.length],
                        topicIndex / COMMONS_TOPICS.length % 2 == 0, (topicIndex / (COMMONS_TOPICS.length * 2)) % 8);
            case NASA:
                String topic = NASA_TOPICS[nasaTopic++ % NASA_TOPICS.length];
                return nasa(topic, 1 + (nasaPage++ / NASA_TOPICS.length) % 5);
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

    private static List<Scene> commons(String topic, boolean cc0, int pageIndex) throws Exception {
        String query = "filetype:bitmap intitle:" + topic + (cc0 ? " hastemplate:CC-Zero"
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
                    || !wide(info.optInt("thumbwidth", info.optInt("width")), info.optInt("thumbheight", info.optInt("height")))) continue;
            result.add(new Scene("commons:" + page.getLong("pageid"), info.optString("thumburl", info.optString("url")), subject, false));
        }
        return result;
    }

    private static List<Scene> nasa(String topic, int page) throws Exception {
        JSONArray images = json("https://images-api.nasa.gov/search?media_type=image&page_size=12&page=" + page
                + "&q=" + encode(topic + " from space")).getJSONObject("collection").getJSONArray("items");
        List<Scene> result = new ArrayList<>();
        for (int i = 0; i < images.length(); i++) {
            JSONObject item = images.getJSONObject(i);
            JSONObject data = item.getJSONArray("data").getJSONObject(0);
            String title = data.optString("title");
            String id = data.optString("nasa_id");
            // NASA astronaut photography avoids third-party media and people in the foreground.
            if (!id.matches("(?i)iss[0-9]+e[0-9]+") || !landscapeSubject(title, data.optString("description"))) continue;
            JSONArray links = item.optJSONArray("links");
            if (links == null) continue;
            for (int j = 0; j < links.length(); j++) {
                JSONObject link = links.getJSONObject(j);
                String url = link.optString("href");
                if (url.endsWith("~medium.jpg") && wide(link.optInt("width"), link.optInt("height"))) {
                    result.add(new Scene("nasa:" + id, url, title, false));
                    break;
                }
            }
        }
        return result;
    }

    /** Visually reviewed solo portraits. The CDN crop is pinned to keep the subject in the wide frame. */
    private static List<Scene> portraits() {
        List<Scene> result = new ArrayList<>();
        portrait(result, "1764740146693-4955d02c98f9", "Outdoor portrait · South Asia", .55); // Akugnc8ROwg
        portrait(result, "1771507057202-353aee0a508e", "Traditional attire · Bangladesh", .37); // DsQtyCdU2QY
        portrait(result, "1778148046680-cd802a2199ea", "Garden portrait · India", .48); // 7wY-3plrc48
        portrait(result, "1745690720220-24e337e571c7", "Maasai portrait · Kenya", .42); // JlK7DNQvRxE
        portrait(result, "1762368229295-81f2742fb8a5", "Schäfler at sunset · Switzerland", .55); // w7h4KYsuYk4
        portrait(result, "1698357856852-100709daa31e", "Autumn in the Alps · Switzerland", .59); // 10-KHNApcRQ
        portrait(result, "1513266913343-a1bfd2ffd806", "Portrait · Peru", .32); // B0U_OFXXSNo
        return result;
    }

    private static void portrait(List<Scene> list, String id, String subject, double focusY) {
        list.add(new Scene("unsplash:" + id, "https://images.unsplash.com/photo-" + id
                + "?fit=crop&crop=focalpoint&fp-x=0.5&fp-y=" + focusY + "&w=1600&h=900&fm=jpg&q=78", subject, true));
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

    static boolean wide(int width, int height) {
        return width >= 960 && height >= 540 && (long) width * 100 >= (long) height * 130;
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
                case "images-assets.nasa.gov": return path.matches("/image/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+~medium\\.jpg");
                default: return false;
            }
        } catch (java.net.URISyntaxException invalid) {
            return false;
        }
    }
}
