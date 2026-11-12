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
    private enum Source { BING, PORTRAITS, COMMONS, AGENCY_PHOTOS }
    private static final Source[] SOURCES = Source.values();
    private static final String[] MARKETS = {"en-US", "en-GB", "en-AU", "en-CA", "de-DE", "ja-JP"};
    private static final String[] COMMONS_TOPICS = {"mountains", "lake", "waterfall", "coast", "desert", "forest"};
    private static final Pattern UNSUITABLE = Pattern.compile(
            "\\b(?:kiss\\w*|couples?|romance|romantic|wedding\\w*|brides?|grooms?|nud\\w*|erotic\\w*|"
            + "sexual\\w*|intimate|lingerie|swimwear|bikinis?|painting\\w*|illustration\\w*|"
            + "nasa|astronaut\\w*|space station|from space|satellite imagery|orbital|artwork\\w*|composites?|people|persons?|families|family|women|woman|children|hikers?|crowds?|portraits?|lovers?|hugging|embracing|group photo)\\b", Pattern.CASE_INSENSITIVE);
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
        addReviewed(Source.AGENCY_PHOTOS, agencyPhotos());
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
            if (source == Source.PORTRAITS || source == Source.AGENCY_PHOTOS) continue;
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
                return commons(COMMONS_TOPICS[topicIndex % COMMONS_TOPICS.length],
                        topicIndex / COMMONS_TOPICS.length % 2 == 0, (topicIndex / (COMMONS_TOPICS.length * 2)) % 8);
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

    /** Visually reviewed portraits of individuals, children and families. */
    private static List<Scene> portraits() {
        List<Scene> result = new ArrayList<>();
        portrait(result, "1764740146693-4955d02c98f9", "Outdoor portrait · South Asia"); // Akugnc8ROwg
        portrait(result, "1771507057202-353aee0a508e", "Traditional attire · Bangladesh"); // DsQtyCdU2QY
        portrait(result, "1778148046680-cd802a2199ea", "Garden portrait · India"); // 7wY-3plrc48
        portrait(result, "1745690720220-24e337e571c7", "Maasai portrait · Kenya"); // JlK7DNQvRxE
        portrait(result, "1762368229295-81f2742fb8a5", "Schäfler at sunset · Switzerland"); // w7h4KYsuYk4
        portrait(result, "1698357856852-100709daa31e", "Autumn in the Alps · Switzerland"); // 10-KHNApcRQ
        portrait(result, "1513266913343-a1bfd2ffd806", "Portrait · Peru"); // B0U_OFXXSNo
        portrait(result, "1747671688812-76d7bca21f34", "Outdoor portrait"); // nGnQpHSZaQ4
        portrait(result, "1767396857436-0692218b0963", "A smile in the park"); // ymknwQNjz5I
        portrait(result, "1754296999921-a507b8684f6a", "Garden portrait"); // dQGsCGtjqmU
        portrait(result, "1743851520478-b05c141d5505", "Street portrait"); // BaRPZe7OsFU
        portrait(result, "1542719018-ee28cbdc71ee", "City portrait"); // Imlqq97CvzQ
        portrait(result, "1779902334062-922f50304615", "Outdoors in the park"); // ZvVddinkvBE
        portrait(result, "1793107240990-e6474550bd1c", "Children outdoors"); // ycQ4T1Bk2vM
        portrait(result, "1758687126864-96b61e1b3af0", "Family at home"); // tdkQ_CLYtCc
        portrait(result, "1792171033850-29d943b3e3b6", "Family in the garden"); // mywYzsx-WsU
        portrait(result, "1761839258657-457dda39b5cc", "Family by the meadow"); // p6wuN_3Wwqs
        portrait(result, "1697906100216-ef4688b26174", "Portrait together"); // VjPB3s86jUw
        portrait(result, "1754623034741-aa49010fa3c8", "Baby outdoors"); // l1ZBZp2SuaY
        portrait(result, "1652666087939-7db7dd87217b", "Playing in the grass"); // 67HraMdC_YQ
        portrait(result, "1753803806122-5166fbd4d0ed", "Baby in the garden"); // g6RLJ32mFIo
        portrait(result, "1676509926048-5410d37f478d", "Baby portrait"); // 8bQR-MwxGfA
        portrait(result, "1720038340904-9bf0c0bd3e9c", "Childhood portrait"); // 5_VtRH1LQdA
        return result;
    }

    private static void portrait(List<Scene> list, String id, String subject) {
        list.add(new Scene("unsplash:" + id, "https://images.unsplash.com/photo-" + id
                + "?fit=max&w=1920&h=1080&fm=jpg&q=78", subject, true));
    }

    /** Public-domain photographs from the U.S. Fish and Wildlife Service and U.S. Geological Survey. */
    private static List<Scene> agencyPhotos() {
        List<Scene> result = new ArrayList<>();
        result.add(new Scene("fws:montana-landscape",
                "https://www.fws.gov/sites/default/files/banner_images/2022-12/Montana%20Landscape%20Aubin%20Douglas%20USFWS.jpg",
                "Montana wetlands", true));
        result.add(new Scene("fws:landscape-view-mountains",
                "https://www.fws.gov/sites/default/files/styles/max_1300x1300/public/images/2006-08/14298.jpg?itok=ugYtOSkn",
                "Mountain grasslands", true));
        result.add(new Scene("fws:mountain-valley-and-lake",
                "https://www.fws.gov/sites/default/files/images/2024-03-2/4452.jpg",
                "Mountain lake", true));
        result.add(new Scene("fws:scenic-mountains",
                "https://www.fws.gov/sites/default/files/styles/max_1300x1300/public/images/2016-07/24097.jpg?itok=VLGeVj9x",
                "Mountains across the valley", true));
        result.add(new Scene("fws:mountain-valley-and-lakes",
                "https://www.fws.gov/sites/default/files/images/2024-03-3/5011.jpg",
                "Lakes in a mountain valley", true));
        result.add(new Scene("usgs:mountain-river-waterfall",
                "https://d9-wret.s3.us-west-2.amazonaws.com/assets/palladium/production/s3fs-public/Biomes_WaterWetlands_InlandWaters_00047.jpg",
                "Mountain river waterfall", true));
        result.add(new Scene("usgs:hayden-run-waterfall",
                "https://d9-wret.s3.us-west-2.amazonaws.com/assets/palladium/production/s3fs-public/Hayden_Run_Waterfall.jpg",
                "Hayden Run waterfall", true));
        result.add(new Scene("usgs:mojave-desert",
                "https://d9-wret.s3.us-west-2.amazonaws.com/assets/palladium/production/s3fs-public/thumbnails/image/MojaveLowElevationUnburnedNoInvasives.JPG",
                "Mojave Desert", true));
        return result;
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

    static boolean usableDimensions(int width, int height, boolean reviewed) {
        if (reviewed) return Math.min(width, height) >= 480 && Math.max(width, height) >= 960;
        return wide(width, height);
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
                case "www.fws.gov": return path.startsWith("/sites/default/files/")
                        && path.toLowerCase(Locale.ROOT).endsWith(".jpg");
                case "d9-wret.s3.us-west-2.amazonaws.com": return path.startsWith("/assets/palladium/production/s3fs-public/")
                        && path.toLowerCase(Locale.ROOT).endsWith(".jpg");
                default: return false;
            }
        } catch (java.net.URISyntaxException invalid) {
            return false;
        }
    }
}
