package org.archive.modules.extractor;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.archive.modules.CrawlURI;
import org.archive.util.UriUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts URIs from JSON resources.
 * <p>
 * n.b. chokes on JSONP, e.g.
 * <p>
 * breakingNews({"pollPeriod":30000,"isError":false,"html":""})
 *
 * @author rcoram
 */
public class ExtractorJson extends ContentExtractor {
    public final static String JSON_URI = "^https?://[^/]+/.+\\.json\\b.*$";
    private static final Logger LOGGER = Logger.getLogger(ExtractorJson.class.getName());
    private final JsonFactory factory = new JsonFactory();
    private final ObjectMapper mapper = new ObjectMapper(factory);

    @Override
    protected boolean innerExtract(CrawlURI curi) {
        try {
            List<String> links = new ArrayList<>();
            JsonNode rootNode = mapper.readTree(curi.getRecorder().getContentReplayInputStream());
            parse(rootNode, links);
            for (String link : links) {
                try {
                    int max = getExtractorParameters().getMaxOutlinks();
                    addRelativeToBase(curi, max, link, LinkContext.INFERRED_MISC, Hop.INFERRED);
                    numberOfLinksExtracted.incrementAndGet();
                } catch (org.archive.url.URIException e) {
                    logUriError(e, curi.getUURI(), link);
                }
            }
        } catch (Exception e) {
            // Only record this as INFO, as malformed JSON is fairly common.
            LOGGER.log(Level.INFO, curi.getURI() + " : " + e.getMessage());
        }
        return false;
    }

    @Override
    protected boolean shouldExtract(CrawlURI curi) {
        String contentType = curi.getContentType();
        if (contentType != null && contentType.contains("json")) {
            return true;
        }
        return curi.isSuccess() && curi.toString().matches(JSON_URI);
    }

    protected List<String> parse(JsonNode rootNode, List<String> links) {
        for (Map.Entry<String, JsonNode> field : rootNode.properties()) {
            parseValue(field.getValue(), links);
        }
        return links;
    }

    private void parseValue(JsonNode value, List<String> links) {
        if (value.textValue() != null && UriUtils.isVeryLikelyUri(value.textValue())) {
            links.add(value.textValue());
        } else if (value.isObject()) {
            parse(value, links);
        } else if (value.isArray()) {
            value.valueStream().forEach(element -> parseValue(element, links));
        }
    }
}