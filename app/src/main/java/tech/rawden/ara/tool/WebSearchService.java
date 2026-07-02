package tech.rawden.ara.tool;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;

public class WebSearchService {

    private static final Logger LOG = Logger.getLogger(WebSearchService.class.getName());
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static String search(String query) {
        LOG.info("Web search requested: \"" + query + "\"");
        try {
            var encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            var uri = URI.create("https://lite.duckduckgo.com/lite/?q=" + encoded + "&kl=wt-wt");
            LOG.fine("Fetching: " + uri);
            var request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (compatible; Ara/4.0)")
                    .build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            LOG.fine("HTTP response: " + response.statusCode() + " ("
                    + response.body().length() + " bytes)");
            if (response.statusCode() != 200) {
                LOG.warning("Search returned HTTP " + response.statusCode());
                return "Search failed: HTTP " + response.statusCode();
            }
            var result = parseResults(response.body());
            var summary = result.length() > 80 ? result.substring(0, 77) + "..." : result;
            LOG.info("Search result (" + result.length() + " chars): " + summary);
            return result;
        } catch (java.net.http.HttpTimeoutException e) {
            LOG.warning("Web search timed out for query: \"" + query + "\"");
            return "Search timed out. Please try again.";
        } catch (Exception e) {
            LOG.warning("Web search failed for query \"" + query + "\": " + e.getMessage());
            return "Search error: " + e.getMessage();
        }
    }

    static String parseResults(String html) {
        LOG.fine("Parsing HTML (" + html.length() + " chars)");
        var sb = new StringBuilder();
        int count = 0;
        int pos = 0;

        while (true) {
            var linkPos = html.indexOf("result-link", pos);
            if (linkPos < 0) break;

            var trOpen = html.lastIndexOf("<tr", linkPos);
            var trClose = html.indexOf(">", trOpen);
            var trTag = trOpen >= 0 && trClose > trOpen ? html.substring(trOpen, trClose + 1) : "";
            var isSponsored = trTag.contains("result-sponsored");

            var aClose = html.indexOf("</a>", linkPos);
            if (aClose < 0) break;
            var titleStart = html.indexOf(">", linkPos) + 1;
            if (titleStart < 0 || titleStart >= aClose) {
                pos = aClose;
                continue;
            }
            var title = html.substring(titleStart, aClose).trim();

            var hrefStart = findHrefStart(html, linkPos);
            var hrefEnd = hrefStart >= 0 ? html.indexOf("\"", hrefStart) : -1;
            var rawUrl = hrefStart >= 0 && hrefEnd > hrefStart ? html.substring(hrefStart, hrefEnd) : "";

            if (!isSponsored) {
                var snippetPos = html.indexOf("result-snippet", aClose);
                String snippet = "";
                if (snippetPos >= 0) {
                    var snipStart = html.indexOf(">", snippetPos) + 1;
                    var snipEnd = html.indexOf("</td>", snipStart);
                    if (snipStart > 0 && snipEnd > snipStart) {
                        snippet = stripHtml(html.substring(snipStart, snipEnd)).trim();
                    }
                }

                count++;
                sb.append(count).append(". ").append(title).append("\n");
                sb.append("   ").append(cleanUrl(rawUrl)).append("\n");
                if (!snippet.isEmpty()) {
                    sb.append("   ").append(snippet).append("\n");
                }
                sb.append("\n");
            }

            pos = aClose;
        }

        var result = sb.toString().trim();
        LOG.fine("Parsed " + count + " organic results");
        return result.isEmpty() ? "No results found." : result;
    }

    private static int findHrefStart(String html, int linkPos) {
        var dq = html.lastIndexOf("href=\"", linkPos);
        var sq = html.lastIndexOf("href='", linkPos);
        if (dq < 0 && sq < 0) return -1;
        if (dq < 0) return sq + 6;
        if (sq < 0) return dq + 6;
        return Math.max(dq, sq) + 6;
    }

    private static String cleanUrl(String rawUrl) {
        if (rawUrl.startsWith("//")) rawUrl = "https:" + rawUrl;
        if (rawUrl.contains("uddg=")) {
            var uddg = rawUrl.substring(rawUrl.indexOf("uddg=") + 5);
            var ampIdx = uddg.indexOf('&');
            if (ampIdx > 0) uddg = uddg.substring(0, ampIdx);
            try {
                return URLDecoder.decode(uddg, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        return rawUrl;
    }

    private static String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#x27;", "'")
                .replace("&quot;", "\"")
                .replace("&nbsp;", " ")
                .replace("&#39;", "'")
                .trim();
    }
}
