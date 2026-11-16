package com.sadique.launcher

import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Collections
import java.util.EnumMap
import java.util.LinkedHashSet
import java.util.Random
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONException
import org.json.JSONObject
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Independent online sources for the preview and Android TV dream. */
internal class LandscapeFeed {

    private enum class Source {
        FLICKR, FLICKR_PEOPLE, OPENVERSE, BING, PORTRAITS, WALLSPAPERS, COMMONS, PUBLIC_DOMAIN_PICTURES, OCEAN_EXPLORATION, NATIONAL_PARK_SERVICE, PXHERE;

        fun live(): Boolean {
            return this == FLICKR || this == FLICKR_PEOPLE || this == OPENVERSE || this == BING || this == COMMONS || this == WALLSPAPERS
        }
    }

    class Scene(
        @JvmField val id: String,
        @JvmField val url: String,
        @JvmField val subject: String,
        @JvmField val reviewed: Boolean,
        @JvmField val cropOffsetPercent: Int = -1,
        @JvmField val livePeople: Boolean = false
    )

    private val random = Random()
    private val queues = EnumMap<Source, ArrayDeque<Scene>>(Source::class.java)
    private val retryAfter = EnumMap<Source, Long>(Source::class.java)
    private val wallpaperPages = ArrayDeque<String>()
    private val examinedWallpaperPages = LinkedHashSet<String>()
    private var nextWallpaperRefresh = 0L
    private var nextSource = 0
    private var recycleSource = 0
    private var flickrTopic = 0
    private var peopleTopic = 0
    private var openverseCall = 0
    private var bingBatch = 0
    private var commonsTopic = 0
    private val bingMarket: Int

    init {
        bingMarket = random.nextInt(MARKETS.size)
        commonsTopic = random.nextInt(COMMONS_TOPICS.size)
        for (source in Source.values()) queues[source] = ArrayDeque()
        addReviewed(Source.PORTRAITS, LandscapeReviewedPhotos.portraits())
        addReviewed(Source.OCEAN_EXPLORATION, LandscapeReviewedPhotos.oceanPhotos())
        addReviewed(Source.WALLSPAPERS, LandscapeReviewedPhotos.wallpapersPhotos())
        addReviewed(Source.PUBLIC_DOMAIN_PICTURES, LandscapeReviewedPhotos.publicDomainPicturesPhotos())
        addReviewed(Source.NATIONAL_PARK_SERVICE, LandscapeReviewedPhotos.nationalParkPhotos())
        addReviewed(Source.PXHERE, LandscapeReviewedPhotos.pxherePhotos())
    }

    private fun addReviewed(source: Source, photos: MutableList<Scene>) {
        Collections.shuffle(photos, random)
        queues.getValue(source).addAll(photos)
    }

    fun next(alreadyShown: Predicate<String>, deadline: Long): Scene? {
        for (i in SOURCES.indices) {
            if (SystemClock.elapsedRealtime() >= deadline || Thread.currentThread().isInterrupted) break
            val source = SOURCES[nextSource]
            nextSource = (nextSource + 1) % SOURCES.size
            val queue = queues.getValue(source)
            var candidate = unseen(queue, alreadyShown)
            if (candidate != null) return candidate
            if (!source.live()) continue
            if (System.nanoTime() < retryAfter.getOrDefault(source, Long.MIN_VALUE)) continue
            try {
                val fetched = fetch(source, alreadyShown)
                Collections.shuffle(fetched, random)
                queue.addAll(fetched)
                candidate = unseen(queue, alreadyShown)
                retryAfter[source] = System.nanoTime() + TimeUnit.SECONDS.toNanos(if (candidate == null) 5 else 8)
                if (candidate != null) return candidate
            } catch (dnsUnavailable: UnknownHostException) {
                // A missing DNS record should not delay the independent photo sources each rotation.
                retryAfter[source] = System.nanoTime() + TimeUnit.MINUTES.toNanos(2)
                Log.w("PersonalLandscape", "$source hostname could not be resolved", dnsUnavailable)
            } catch (unavailable: Exception) {
                Log.w("PersonalLandscape", "$source photo feed unavailable", unavailable)
                retryAfter[source] = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            }
        }
        // Second pass: recycle reviewed photos when no unseen photo exists
        // anywhere. The reviewed lists are static, so refill each queue and
        // ignore the already-shown check — the slideshow must never starve.
        for (i in REVIEWED_SOURCES.indices) {
            if (SystemClock.elapsedRealtime() >= deadline || Thread.currentThread().isInterrupted) break
            val source = REVIEWED_SOURCES[recycleSource]
            recycleSource = (recycleSource + 1) % REVIEWED_SOURCES.size
            val queue = queues.getValue(source)
            if (queue.isEmpty()) addReviewed(source, reviewedPhotos(source))
            val candidate = recycled(queue)
            if (candidate != null) return candidate
        }
        return null
    }

    private fun flickrRss(tags: String): ByteArray {
        return OnlineContent.get(
            "https://www.flickr.com/services/feeds/photos_public.gne" +
                "?format=rss_200&tagmode=all&tags=" + encode(tags), 192 * 1024, 2200, 3500
        )
    }

    private fun fetch(source: Source, alreadyShown: Predicate<String>): MutableList<Scene> {
        return when (source) {
            Source.FLICKR -> flickrScenes(flickrRss(FLICKR_TAGS[flickrTopic++ % FLICKR_TAGS.size]))
            Source.FLICKR_PEOPLE -> {
                val topic = peopleTopic++ % (PEOPLE_REGIONS.size * PEOPLE_TAGS.size)
                val region = PEOPLE_REGIONS[topic % PEOPLE_REGIONS.size]
                val tag = PEOPLE_TAGS[topic / PEOPLE_REGIONS.size]
                flickrPeopleScenes(flickrRss("$region,$tag"))
            }
            Source.OPENVERSE -> {
                val call = openverseCall++
                val topic = FLICKR_TAGS[call % FLICKR_TAGS.size]
                openverseScenes(
                    OnlineContent.get(
                        "https://api.openverse.org/v1/images/?q=" + encode(topic) +
                            "&source=flickr&page_size=20&page=" + ((call / FLICKR_TAGS.size) % 12 + 1),
                        128 * 1024, 3500, 5000
                    ),
                    topic
                )
            }
            Source.BING -> {
                val batchIndex = bingBatch++ % (MARKETS.size * 2)
                bing(
                    MARKETS[(bingMarket + batchIndex % MARKETS.size) % MARKETS.size],
                    if (batchIndex < MARKETS.size) 0 else 7
                )
            }
            Source.WALLSPAPERS -> liveWallpapers(alreadyShown)
            Source.COMMONS -> {
                val topicIndex = commonsTopic++
                val batch = topicIndex / COMMONS_TOPICS.size
                commons(
                    COMMONS_TOPICS[topicIndex % COMMONS_TOPICS.size],
                    COMMONS_SIZES[batch % COMMONS_SIZES.size], batch % 8
                )
            }
            else -> throw AssertionError(source)
        }
    }

    private fun liveWallpapers(alreadyShown: Predicate<String>): MutableList<Scene> {
        val now = System.nanoTime()
        if (wallpaperPages.isEmpty() && (nextWallpaperRefresh == 0L || now >= nextWallpaperRefresh)) {
            val feed = wallpaperText("$WALLSPAPERS_HOST/feed.xml", 96 * 1024)
            val links = wallpaperLinks(feed)
            examinedWallpaperPages.retainAll(links)
            for (link in links) {
                if (!examinedWallpaperPages.contains(link)) wallpaperPages.add(link)
            }
            nextWallpaperRefresh = now + TimeUnit.MINUTES.toNanos(3)
        }
        for (i in 0 until 2) {
            if (wallpaperPages.isEmpty() || Thread.currentThread().isInterrupted) break
            val link = wallpaperPages.peek() ?: break
            val scene: Scene?
            try {
                scene = wallpaperScene(wallpaperText(link, 96 * 1024))
            } catch (unavailable: IOException) {
                // A single removed or unreachable page must not stall the rest of this feed.
                wallpaperPages.remove()
                continue
            }
            wallpaperPages.remove()
            examinedWallpaperPages.add(link)
            if (scene != null && !alreadyShown.test(scene.id)) return mutableListOf(scene)
        }
        return mutableListOf()
    }

    companion object {
        // Give Flickr the first attempt and two additional turns in each rotation.
        private val SOURCES = arrayOf(
            Source.FLICKR, Source.FLICKR_PEOPLE, Source.OPENVERSE, Source.BING, Source.PORTRAITS, Source.FLICKR,
            Source.WALLSPAPERS, Source.COMMONS, Source.PUBLIC_DOMAIN_PICTURES, Source.FLICKR,
            Source.OCEAN_EXPLORATION, Source.NATIONAL_PARK_SERVICE, Source.PXHERE
        )
        // Reviewed sources that can be recycled when no unseen photo exists.
        private val REVIEWED_SOURCES = arrayOf(
            Source.PORTRAITS, Source.OCEAN_EXPLORATION, Source.WALLSPAPERS,
            Source.PUBLIC_DOMAIN_PICTURES, Source.NATIONAL_PARK_SERVICE, Source.PXHERE
        )
        private val FLICKR_TAGS = arrayOf(
            "lake", "landscape", "mountains", "nature", "forest", "coast", "waterfall",
            "sunset", "glacier", "river", "ocean", "nationalpark", "desert", "beach",
            "island", "valley", "friends", "family", "portrait", "people", "street",
            "festival", "travel", "city"
        )
        private val PEOPLE_REGIONS = arrayOf(
            "pakistan", "india", "iran", "tajikistan", "turkmenistan", "saudiarabia", "unitedarabemirates",
            "oman", "qatar", "kuwait", "bahrain", "iraq", "jordan", "egypt", "lebanon",
            "turkey", "afghanistan", "uzbekistan", "kyrgyzstan", "kazakhstan", "bangladesh", "nepal",
            "srilanka", "azerbaijan"
        )
        private val PEOPLE_TAGS = arrayOf(
            "education", "students", "women", "family", "people", "portrait", "streetphotography", "cc0", "publicdomain"
        )
        private val PEOPLE_SUBJECT = Pattern.compile(
            "\\b(?:people|portraits?|women|woman|ladies|lady|men|man|family|families|" +
                "students?|pupils?|schoolgirls?|schoolchildren|classmates?|" +
                "friends|shoppers?|vendors?|sellers?|workers?|walkers?|artists?|drivers?|crowd)\\b",
            Pattern.CASE_INSENSITIVE
        )
        private val PEOPLE_EXCLUDED = Pattern.compile(
            "\\b(?:kiss\\w*|couples?|romanc\\w*|wedding\\w*|brid\\w*|grooms?|nud\\w*|erotic\\w*|" +
                "sex\\w*|intimat\\w*|suggestive|lingerie|underwear|swimwear|bikinis?|topless|fetish|bdsm|" +
                "boudoir|see.through|sheer|thongs?|bedroom|bed|provocative|sensual|glamour|hugg\\w*|embrac\\w*|" +
                "paintings?|illustrations?|artwork|ai.generated|generative|midjourney|stable diffusion|" +
                "protest\\w*|violence|weapons?|firearms?|wars?|conflict|nasa|astronaut\\w*|satellite)\\b",
            Pattern.CASE_INSENSITIVE
        )
        private const val MEDIA_RSS = "http://search.yahoo.com/mrss/"

        private const val WALLSPAPERS_HOST = "https://wallpapers.org"
        private val WALLPAPER_LINK = Pattern.compile(
            "<link>(https://wallpapers\\.org/wallpaper/[a-z0-9-]{1,160})</link>"
        )
        private val IMAGE_METADATA = Pattern.compile(
            "<script type=\"application/ld\\+json\">([^<]+)</script>"
        )
        private val FHD_DOWNLOAD = Pattern.compile(
            "href=\"(/download/[a-f0-9]{10}/fhd)\""
        )
        // Old Flickr photo URLs carry no size suffix (500px); the Openverse parser rewrites them to _b.
        private val STATICFLICKR_PLAIN = Pattern.compile(
            "https://live\\.staticflickr\\.com/[0-9]+/[0-9]+_[0-9a-f]+\\.jpg"
        )
        private val MARKETS = arrayOf("en-US", "en-GB", "en-AU", "en-CA", "de-DE", "ja-JP")
        private val COMMONS_TOPICS = arrayOf("mountain", "lake", "waterfall", "coast", "desert", "forest", "beach", "river")
        private val COMMONS_SIZES = arrayOf(
            intArrayOf(1920, 1080), intArrayOf(1280, 720), intArrayOf(1600, 900),
            intArrayOf(2560, 1440), intArrayOf(3840, 2160)
        )

        private fun unseen(queue: ArrayDeque<Scene>, alreadyShown: Predicate<String>): Scene? {
            while (!queue.isEmpty()) {
                val item = queue.removeFirst()
                if (imageUrlAllowed(item.url) && !alreadyShown.test(item.id)) return item
            }
            return null
        }

        private fun recycled(queue: ArrayDeque<Scene>): Scene? {
            while (!queue.isEmpty()) {
                val item = queue.removeFirst()
                if (imageUrlAllowed(item.url)) return item
            }
            return null
        }

        private fun reviewedPhotos(source: Source): MutableList<Scene> {
            return when (source) {
                Source.PORTRAITS -> LandscapeReviewedPhotos.portraits()
                Source.OCEAN_EXPLORATION -> LandscapeReviewedPhotos.oceanPhotos()
                Source.WALLSPAPERS -> LandscapeReviewedPhotos.wallpapersPhotos()
                Source.PUBLIC_DOMAIN_PICTURES -> LandscapeReviewedPhotos.publicDomainPicturesPhotos()
                Source.NATIONAL_PARK_SERVICE -> LandscapeReviewedPhotos.nationalParkPhotos()
                Source.PXHERE -> LandscapeReviewedPhotos.pxherePhotos()
                else -> mutableListOf()
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun flickrScenes(feed: ByteArray): MutableList<Scene> {
            return flickrScenes(feed, false)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun flickrPeopleScenes(feed: ByteArray): MutableList<Scene> {
            return flickrScenes(feed, true)
        }

        private fun flickrScenes(feed: ByteArray, people: Boolean): MutableList<Scene> {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            // Flickr publishes UTF-8 RSS. Reject DTDs before parsing so the parser cannot resolve entities.
            val rss = String(feed, StandardCharsets.UTF_8)
            if (rss.contains("<!DOCTYPE")) return mutableListOf()
            val xml = factory.newDocumentBuilder().parse(InputSource(StringReader(rss)))
            val items = xml.getElementsByTagName("item")
            val photos = ArrayList<Scene>()
            for (i in 0 until Math.min(items.length, 20)) {
                val item = items.item(i) as Element
                val content = item.getElementsByTagNameNS(MEDIA_RSS, "content").item(0)
                if (content !is Element) continue
                val image = content
                val width = xmlDimension(image, "width")
                val height = xmlDimension(image, "height")
                if ("image/jpeg" != image.getAttribute("type") ||
                    !(if (people) fitsPeopleFrame(width, height) else fitsWidescreen(width, height))
                ) continue
                val title = xmlText(item, MEDIA_RSS, "title")
                val description = xmlText(item, null, "description").replace(Regex("<[^>]*>"), " ")
                val tags = xmlText(item, MEDIA_RSS, "category")
                val text = "$title $description $tags"
                if (people) {
                    // Require a meaningful people subject in the title or caption, not just a search tag.
                    if (title.isEmpty() || !PEOPLE_SUBJECT.matcher("$title $description").find() ||
                        PEOPLE_EXCLUDED.matcher(text).find() || !safeSubject(title, "$description $tags")) continue
                } else if (!safeSubject(title, "$description $tags")) continue
                val url = image.getAttribute("url")
                if (!imageUrlAllowed(url)) continue
                val uri = URI.create(url)
                if ("live.staticflickr.com" != uri.host) continue
                val id = LandscapePhotoPolicy.flickrImage.matcher(uri.path)
                if (id.matches()) photos.add(Scene("flickr:" + id.group(1), url, title, false, -1, people))
            }
            return photos
        }

        @JvmStatic
        @Throws(Exception::class)
        fun openverseScenes(feed: ByteArray, topic: String): MutableList<Scene> {
            val photos = ArrayList<Scene>()
            val results = JSONObject(String(feed, StandardCharsets.UTF_8)).optJSONArray("results") ?: return photos
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isEmpty()) continue
                var url = item.optString("url")
                if (url.isEmpty()) continue
                // Old Flickr URLs carry no size suffix (500px); rewrite to the 1024px _b variant.
                if (STATICFLICKR_PLAIN.matcher(url).matches()) url = url.substring(0, url.length - 4) + "_b.jpg"
                if (!imageUrlAllowed(url)) continue
                val width = item.optInt("width")
                val height = item.optInt("height")
                if (!fitsWidescreen(width, height)) continue
                val title = item.optString("title").trim().ifEmpty { topic }
                val tags = item.optJSONArray("tags")
                val tagText = StringBuilder()
                if (tags != null) {
                    for (j in 0 until tags.length()) {
                        tagText.append(tags.optJSONObject(j)?.optString("name") ?: "").append(' ')
                    }
                }
                if (!safeSubject(title, tagText.toString())) continue
                photos.add(Scene("openverse:$id", url, title, false))
            }
            return photos
        }

        private fun xmlDimension(element: Element, attribute: String): Int {
            try {
                return Integer.parseInt(element.getAttribute(attribute))
            } catch (invalid: NumberFormatException) {
                return 0
            }
        }

        private fun xmlText(item: Element, namespace: String?, name: String): String {
            val node = item.getElementsByTagNameNS(namespace, name).item(0)
            return if (node == null) "" else node.textContent.trim()
        }

        private fun wallpaperText(url: String, limit: Int): String {
            return String(OnlineContent.get(url, limit, 2000, 3000), StandardCharsets.UTF_8)
        }

        @JvmStatic
        fun wallpaperLinks(feed: String): Set<String> {
            val links = LinkedHashSet<String>()
            val items = WALLPAPER_LINK.matcher(feed)
            while (links.size < 50 && items.find()) links.add(items.group(1))
            return links
        }

        @JvmStatic
        @Throws(JSONException::class)
        fun wallpaperScene(page: String): Scene? {
            val downloads = FHD_DOWNLOAD.matcher(page)
            if (!downloads.find()) return null
            val path = downloads.group(1)
            val metadata = IMAGE_METADATA.matcher(page)
            while (metadata.find()) {
                val image = JSONObject(metadata.group(1))
                if ("ImageObject" != image.optString("@type")) continue
                val title = image.optString("name").trim()
                val description = image.optString("description").trim()
                val keywords = image.optString("keywords")
                if (description.isEmpty() || !safeSubject(title, "$description $keywords")) return null
                return Scene(
                    "wallpapers:" + path.substring("/download/".length, path.length - "/fhd".length),
                    WALLSPAPERS_HOST + path, title, false
                )
            }
            return null
        }

        private fun bing(market: String, archiveStart: Int): MutableList<Scene> {
            val host = "https://www.bing.com"
            val images = json("$host/HPImageArchive.aspx?format=js&idx=$archiveStart&n=8&mkt=$market").getJSONArray("images")
            val result = ArrayList<Scene>()
            for (i in 0 until images.length()) {
                val item = images.getJSONObject(i)
                val base = item.optString("urlbase")
                val subject = item.optString("copyright").split(Regex(" \\(©"), 2)[0].trim()
                if (!base.startsWith("/th?id=OHR.") || !safeSubject(subject, item.optString("title"))) continue
                val imageId = base.substring("/th?id=OHR.".length).split("_", limit = 2)[0]
                result.add(Scene("bing:$imageId", host + base + "_1920x1080.jpg", subject, false))
            }
            return result
        }

        private fun commons(topic: String, size: IntArray, pageIndex: Int): MutableList<Scene> {
            val dimensions = if (pageIndex % 2 == 0) " filew:" + size[0] + " fileh:" + size[1] else ""
            val query = "filetype:bitmap" + dimensions + " intitle:" + topic
            val response = json(
                "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2" +
                    "&generator=search&gsrnamespace=6&gsrlimit=16&gsroffset=" + pageIndex * 16 + "&gsrsearch=" + encode(query) +
                    "&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiurlwidth=1600" +
                    "&iiextmetadatafilter=LicenseShortName%7CObjectName%7CImageDescription"
            )
            val result = ArrayList<Scene>()
            val data = response.optJSONObject("query")
            if (data == null) return result
            val pages = data.getJSONArray("pages")
            for (i in 0 until pages.length()) {
                val page = pages.getJSONObject(i)
                val infoList = page.optJSONArray("imageinfo")
                val info = infoList?.optJSONObject(0)
                if (info == null) continue
                val meta = info.optJSONObject("extmetadata")
                if (meta == null) continue
                val title = page.getString("title").replaceFirst(Regex("^File:"), "").replaceFirst(Regex("(?i)\\.jpe?g$"), "")
                val objectName = metadata(meta, "ObjectName")
                val subject = if (objectName.isEmpty()) title else objectName
                if ("image/jpeg" != info.optString("mime") ||
                    !safeSubject(title, subject + " " + metadata(meta, "ImageDescription")) ||
                    !fitsWidescreen(info.optInt("width"), info.optInt("height"))
                ) continue
                val url = if (fitsWidescreen(info.optInt("thumbwidth"), info.optInt("thumbheight")))
                    info.optString("thumburl") else info.optString("url")
                result.add(Scene("commons:" + page.getLong("pageid"), url, subject, false))
            }
            return result
        }

        private fun json(url: String): JSONObject {
            return JSONObject(String(OnlineContent.get(url, 256 * 1024, 3500, 5000), StandardCharsets.UTF_8))
        }

        private fun encode(value: String): String {
            return URLEncoder.encode(value, "UTF-8")
        }

        private fun metadata(metadata: JSONObject, key: String): String {
            val field = metadata.optJSONObject(key)
            return if (field == null) "" else field.optString("value").replace(Regex("<[^>]*>"), " ").trim()
        }

        @JvmStatic
        fun safeSubject(title: String, description: String): Boolean = LandscapePhotoPolicy.safeSubject(title, description)

        @JvmStatic
        fun commonsLicense(license: String): Boolean = LandscapePhotoPolicy.commonsLicense(license)

        @JvmStatic
        fun fitsWidescreen(width: Int, height: Int): Boolean = LandscapePhotoPolicy.fitsWidescreen(width, height)

        fun fitsPeopleFrame(width: Int, height: Int): Boolean {
            return width >= 960 && width <= 1920 && height > 480 &&
                width.toLong() * 3 >= height.toLong() * 4 &&
                width.toLong() * 9 <= height.toLong() * 16
        }

        @JvmStatic
        fun imageUrlAllowed(url: String): Boolean = LandscapePhotoPolicy.imageUrlAllowed(url)

    }
}
