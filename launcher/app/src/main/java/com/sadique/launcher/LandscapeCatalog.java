package com.sadique.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Multiple metadata-backed sources for the screensaver. No API keys, scraping, or remote
 * configuration. The older unlabelled Picsum random-photo route is deliberately NOT used:
 * its subjects cannot be screened before a download.
 *
 * Commons results are limited to CC0/public-domain images. Non-romantic portraits and people
 * are welcome; titles, descriptions and provider captions are screened for intimate content.
 * Metadata filters cannot guarantee that an image has no inappropriate visual content.
 */
final class LandscapeCatalog {
    static final class Entry {
        final String id, url, subject;
        Entry(String id, String url, String subject) {
            this.id=id; this.url=url; this.subject=subject;
        }
    }

    private static final String[] QUERIES = {
        "mountain landscape", "forest landscape", "waterfall nature", "ocean coast",
        "northern lights landscape", "beautiful city architecture",
        "street photography people", "outdoor people portrait", "travel photography people",
        "wildlife nature", "sunset landscape", "beautiful garden"
    };
    private static final String[] REJECT = {
        "kiss", "kissing", "make out", "making out", "romantic", "romance",
        "intimate", "intimacy", "sexual", "erotic", "nudity", "nude", "naked",
        "lingerie", "porn", "boudoir", "bedroom", "honeymoon",
        "bride", "groom", "wedding", "couple", "lover", "embrace", "hug"
    };
    private LandscapeCatalog() {}

    static boolean familySafe(String subject) {
        if (subject == null) return false;
        String s=subject.toLowerCase(Locale.ROOT).replace('_',' ').replace('-', ' ');
        for (String word: REJECT) if(s.contains(word)) return false;
        return true;
    }

    static boolean imageUrlAllowed(String value) {
        try {
            URI uri=new URI(value);
            String host=uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host!=null && (host.equalsIgnoreCase("upload.wikimedia.org")
                    || host.equalsIgnoreCase("www.bing.com"))
                    && uri.getUserInfo()==null && (uri.getPort()==-1 || uri.getPort()==443);
        } catch (Exception invalid) { return false; }
    }

    static List<Entry> bing(String json) throws Exception {
        JSONObject root=new JSONObject(json);
        JSONArray images=root.optJSONArray("images");
        List<Entry> out=new ArrayList<>();
        if(images==null) return out;
        for(int i=0;i<images.length();i++){
            JSONObject item=images.optJSONObject(i);
            if(item==null) continue;
            String title=item.optString("title","");
            String copyright=item.optString("copyright","");
            String description=title+" "+copyright;
            if(!familySafe(description)) continue;
            String relative=item.optString("url","");
            String url=relative.startsWith("/")?"https://www.bing.com"+relative:relative;
            String hash=item.optString("hsh",item.optString("startdate",""));
            if(hash.isEmpty()||!imageUrlAllowed(url))continue;
            out.add(new Entry("bing:"+hash,url,title.isEmpty()?copyright:title));
        }
        return out;
    }

    static List<Entry> commons(String json) throws Exception {
        JSONObject root=new JSONObject(json);
        JSONObject pages=root.optJSONObject("query");
        if(pages==null) return Collections.emptyList();
        JSONArray items=pages.optJSONArray("pages");
        if(items==null)return Collections.emptyList();
        List<Entry> out=new ArrayList<>();
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(item==null)continue;
            String title=item.optString("title","");
            JSONArray info=item.optJSONArray("imageinfo");
            if(info==null||info.length()==0)continue;
            JSONObject file=info.optJSONObject(0);
            if(file==null)continue;
            int width=file.optInt("width",0),height=file.optInt("height",0);
            if(width<1280||height<720||width*1.0/height<1.4)continue;
            JSONObject meta=file.optJSONObject("extmetadata");
            if(meta==null)continue;
            String license=metadata(meta,"LicenseShortName").toLowerCase(Locale.ROOT);
            // Avoid attribution-required Commons works in a subject-only screensaver.
            if(!license.contains("public domain")&&!license.contains("cc0")&&!license.equals("pd"))continue;
            String subject=(title.startsWith("File:")?title.substring(5):title)
                    .replaceAll("\\.[A-Za-z0-9]{2,5}$","").replace('_',' ');
            String description=subject+" "+metadata(meta,"ImageDescription");
            if(!familySafe(description))continue;
            String url=file.optString("thumburl",file.optString("url",""));
            if(!imageUrlAllowed(url))continue;
            out.add(new Entry("commons:"+item.optInt("pageid"),url,subject));
        }
        return out;
    }

    private static String metadata(JSONObject data,String key) {
        JSONObject item=data.optJSONObject(key);
        return item==null?"":item.optString("value","").replaceAll("<[^>]*>"," ");
    }

    static List<Entry> discover(int seed) throws IOException {
        Random random=new Random(seed);
        List<Entry> found=new ArrayList<>();
        // Fetch from both independent providers; one outage must not take down the entire slide show.
        try {
            found.addAll(bing(OnlineContent.json(
                    "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US")));
        } catch(Exception unavailable) {
            android.util.Log.w("PersonalLandscape","Bing image catalog unavailable",unavailable);
        }
        for(int k=0;k<2;k++){
            String words=QUERIES[Math.floorMod(seed+k*5,QUERIES.length)];
            try {
                String query="filetype:bitmap "+words+" -kiss -kissing -romantic -nude -intimate";
                String endpoint="https://commons.wikimedia.org/w/api.php?action=query"
                        +"&generator=search&gsrnamespace=6&gsrlimit=12"
                        +"&gsrsearch="+URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                        +"&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&iiurlwidth=1920"
                        +"&format=json&formatversion=2";
                found.addAll(commons(new String(OnlineContent.get(endpoint, 256*1024),
                        StandardCharsets.UTF_8)));
            } catch(Exception unavailable){
                android.util.Log.w("PersonalLandscape","Commons image catalog unavailable",unavailable);
            }
        }
        Collections.shuffle(found,random);
        return found;
    }
}
