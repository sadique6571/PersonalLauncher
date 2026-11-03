package com.sadique.launcher;

import java.net.URI;

/** Original Picsum image route: no category, subject or orientation filter. */
final class LandscapeFeed {
    private LandscapeFeed() { }
    static final class Scene {
        final String id,url;
        Scene(String id,String url) { this.id=id; this.url=url; }
    }
    static Scene photo(int seed,long nonce) {
        String key="pl"+Integer.toUnsignedString(seed)+"_"+Long.toUnsignedString(nonce);
        return new Scene("picsum-seed:"+key,"https://picsum.photos/seed/"+key+"/1920/1080");
    }
    static boolean imageUrlAllowed(String url) {
        try {
            URI uri=new URI(url);
            return "https".equals(uri.getScheme()) && "picsum.photos".equals(uri.getHost())
                    && uri.getUserInfo()==null && (uri.getPort()==-1 || uri.getPort()==443)
                    && uri.getPath().matches("/seed/pl[0-9]+_[0-9]+/1920/1080");
        } catch (java.net.URISyntaxException invalid) { return false; }
    }
}
