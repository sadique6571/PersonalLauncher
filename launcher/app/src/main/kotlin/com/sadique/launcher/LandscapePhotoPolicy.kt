package com.sadique.launcher

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import java.util.regex.Pattern

internal object LandscapePhotoPolicy {
    val flickrImage = Pattern.compile("/[0-9]+/([0-9]+)_[a-f0-9]+_b\\.jpg")
    private val UNSUITABLE = Pattern.compile(
        "\\b(?:kiss\\w*|couples?|romance|romantic|wedding\\w*|brides?|grooms?|nud\\w*|erotic\\w*|" +
            "sexual\\w*|intimate|lingerie|swimwear|bikinis?|painting\\w*|illustration\\w*|" +
            "nasa|astronaut\\w*|space station|from space|satellite|cira|orbital|artwork\\w*|composites?|people|persons?|families|family|women|woman|children|hikers?|crowds?|portraits?|lovers?|hugging|embracing|group photo)\\b",
        Pattern.CASE_INSENSITIVE
    )
    private val NATURE = Pattern.compile(
        "\\b(?:mountains?|lakes?|waterfalls?|forests?|parks?|glaciers?|islands?|coasts?|" +
            "canyons?|deserts?|valleys?|rivers?|reefs?|oceans?|cliffs?|dunes?|peaks?|beach(?:es)?|harbou?rs?|grasslands?|" +
            "volcano\\w*|waterways?|aurora\\w*|meadows?|wetlands?|landscapes?)\\b", Pattern.CASE_INSENSITIVE
    )
    fun landscapeSubject(title: String, description: String): Boolean {
        return title.trim().isNotEmpty() && NATURE.matcher("$title $description").find() &&
            !UNSUITABLE.matcher("$title $description").find()
    }

    fun commonsLicense(license: String): Boolean {
        val name = license.trim().lowercase(Locale.ROOT)
        return name == "public domain" || name == "cc0" || name.startsWith("cc0 ")
    }

    fun fitsWidescreen(width: Int, height: Int): Boolean {
        // Accept standard landscape formats close to 16:9. FIT_CENTER shows the full photo.
        if (height <= 480) return false
        val ratio = width.toDouble() / height
        return ratio >= 1.50 && ratio <= 2.05
    }

    fun imageUrlAllowed(url: String): Boolean {
        try {
            val uri = URI(url)
            if ("https" != uri.scheme || uri.host == null || uri.userInfo != null ||
                (uri.port != -1 && uri.port != 443)
            ) return false
            val path = uri.path
            return when (uri.host) {
                "live.staticflickr.com" -> uri.rawQuery == null && flickrImage.matcher(path).matches()
                "images.unsplash.com" -> path.matches(Regex("/photo-[0-9]+-[a-f0-9]+"))
                "www.bing.com" -> "/th" == path && uri.query != null &&
                    uri.query.matches(Regex("id=OHR\\.[A-Za-z0-9_.-]+_1920x1080\\.jpg"))
                "thumb.wikimedia.org",
                "upload.wikimedia.org" -> path.startsWith("/wikipedia/commons/") &&
                    path.lowercase(Locale.ROOT).matches(Regex(".+\\.jpe?g"))
                "wallspapers.org" -> path.matches(Regex("/download/[a-f0-9]{10}/fhd"))
                "www.publicdomainpictures.net" ->
                    path.matches(Regex("/pictures/[0-9]+/velka/[A-Za-z0-9_-]+\\.jpg"))
                "npgallery.nps.gov" -> path.matches(Regex("/GetAsset/[a-f0-9-]{36}/original\\.jpg"))
                "c.pxhere.com" -> path.matches(Regex("/photos/[a-f0-9]{2}/[a-f0-9]{2}/[a-z0-9_-]+\\.jpg!d"))
                "oceanexplorer.noaa.gov" -> path.startsWith("/wp-content/uploads/") &&
                    path.lowercase(Locale.ROOT).endsWith(".jpg")
                else -> false
            }
        } catch (invalid: URISyntaxException) {
            return false
        }
    }
}
