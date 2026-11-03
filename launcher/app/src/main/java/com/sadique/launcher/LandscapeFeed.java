package com.sadique.launcher;

import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Fresh public-domain nature photographs with their source descriptions. */
final class LandscapeFeed {
    private static final String[] QUERIES={
        "mountain lake landscape", "forest river landscape", "beach coastal landscape",
        "desert landscape", "waterfall landscape", "mountain valley landscape"
    };
    private static final Pattern NATURE=Pattern.compile("\\b(mountains?|lakes?|forests?|rivers?|beach(es)?|coast(al)?|desert|dunes?|waterfalls?|valleys?|canyon|cliffs?|fjord|landscape|seascape)\\b");
    private static final Pattern OTHER=Pattern.compile("\\b(portrait|painting|drawing|illustration|diagram|map|logo|poster|collage|statue|interior|cityscape|townscape|birds?|animals?|people)\\b");
    private LandscapeFeed() { }

    static String queryUrl(int seed,long nonce) {
        try {
            String query="filetype:bitmap "+QUERIES[Math.floorMod(seed,QUERIES.length)];
            return "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2"
                    +"&generator=search&gsrnamespace=6&gsrlimit=12&gsrsort=random&gsrsearch="
                    +URLEncoder.encode(query,"UTF-8")
                    +"&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiurlwidth=1920&iiurlheight=1080"
                    +"&iiextmetadatalanguage=en&iiextmetadatafilter=ImageDescription%7CObjectName%7CLicenseShortName%7CCopyrighted%7CAttributionRequired%7CCategories"
                    +"&maxage=0&smaxage=0&requestid="+Long.toUnsignedString(nonce);
        } catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }

    static final class Scene {
        final String id,url,caption;
        Scene(String id,String url,String caption) { this.id=id; this.url=url; this.caption=caption; }
    }

    static List<Scene> scenes(String body,Predicate<String> seen) throws JSONException {
        JSONObject root=new JSONObject(body);
        if (root.has("error")) throw new JSONException("Landscape source unavailable");
        List<Scene> result=new ArrayList<>();
        JSONObject query=root.optJSONObject("query");
        JSONArray pages=query==null?null:query.optJSONArray("pages");
        if (pages==null) return result;
        for (int i=0;i<pages.length();i++) {
            JSONObject page=pages.optJSONObject(i);
            if (page==null || page.optLong("pageid",0)<=0) continue;
            String id="commons:"+page.getLong("pageid");
            if (seen.test(id)) continue;
            JSONArray images=page.optJSONArray("imageinfo");
            JSONObject info=images==null?null:images.optJSONObject(0);
            if (info==null || !LandscapeModel.isLandscape(info.optInt("width"),info.optInt("height"))) continue;
            if (info.optInt("width")<1280 || info.optInt("height")<600) continue;
            String mime=info.optString("mime");
            if (!mime.equals("image/jpeg") && !mime.equals("image/png") && !mime.equals("image/webp")) continue;
            JSONObject meta=info.optJSONObject("extmetadata");
            String license=value(meta,"LicenseShortName").toLowerCase(Locale.ROOT);
            if (!license.equals("public domain") && !license.startsWith("cc0")) continue;
            if (value(meta,"Copyrighted").equalsIgnoreCase("true")
                    || value(meta,"AttributionRequired").equalsIgnoreCase("true")) continue;
            String caption=caption(value(meta,"ImageDescription"));
            if (caption.isEmpty()) caption=caption(value(meta,"ObjectName"));
            if (caption.isEmpty()) continue;
            String subject=(page.optString("title")+" "+caption).toLowerCase(Locale.ROOT);
            if (OTHER.matcher(subject).find()) continue;
            if (!NATURE.matcher(subject+" "+value(meta,"Categories").toLowerCase(Locale.ROOT)).find()) continue;
            String url=info.optString("thumburl");
            if (!imageUrlAllowed(url)) continue;
            result.add(new Scene(id,url,caption));
        }
        return result;
    }

    private static String value(JSONObject meta,String key) {
        JSONObject entry=meta==null?null:meta.optJSONObject(key);
        return entry==null?"":entry.optString("value","");
    }

    static boolean imageUrlAllowed(String url) {
        try {
            URI uri=new URI(url);
            return "https".equals(uri.getScheme()) && uri.getUserInfo()==null
                    && (uri.getPort()==-1 || uri.getPort()==443)
                    && ("upload.wikimedia.org".equals(uri.getHost()) || "thumb.wikimedia.org".equals(uri.getHost()));
        } catch (java.net.URISyntaxException invalid) { return false; }
    }

    static String caption(String html) {
        String text=html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>","")
                .replaceAll("(?i)<br\\s*/?>|</p>|</div>","\n").replaceAll("<[^>]*>","")
                .replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'")
                .replace("&nbsp;"," ").replace("&lt;","<").replace("&gt;",">");
        for (String line:text.split("[\r\n]+")) {
            String cleaned=line.replaceAll("(?i)(photo(?:graph)? by|photographer:|credit:|copyright|©).*$","")
                    .replaceAll("\\b\\d{3,5}\\s*[x×]\\s*\\d{3,5}\\b","")
                    .replaceAll("[\\p{Cntrl}]","").replaceAll("\\s+"," ").trim();
            if (!cleaned.isEmpty()) return cleaned.length()>180?cleaned.substring(0,177)+"…":cleaned;
        }
        return "";
    }
}
