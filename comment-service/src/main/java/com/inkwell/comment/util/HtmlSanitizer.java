package com.inkwell.comment.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class HtmlSanitizer {

    private static final Safelist SAFE_LIST = Safelist.basicWithImages()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "pre", "code", "blockquote")
            .addAttributes(":all", "class", "id")
            .addAttributes("a", "href", "target", "rel")
            .addAttributes("img", "src", "alt", "width", "height");

    public static String sanitize(String html) {
        if (html == null) return null;
        return Jsoup.clean(html, SAFE_LIST);
    }

    public static String stripAll(String html) {
        if (html == null) return null;
        return Jsoup.clean(html, Safelist.none());
    }
}
