package org.archive.modules.postprocessor;

import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.archive.util.ArchiveUtils;
import org.archive.util.MimetypeUtils;
import org.json.JSONObject;

public class CrawlLogJsonBuilder {

    protected static Object checkForNull(Object o) {
        return o != null ? o : JSONObject.NULL;
    }

    public static JSONObject buildJson(CrawlURI curi, Map<String,String> extraFields, ServerCache serverCache) {
        JSONObject jo = new JSONObject();

        jo.put("timestamp", ArchiveUtils.getLog17Date(System.currentTimeMillis()));

        for (Entry<String, String> entry: extraFields.entrySet()) {
            jo.put(entry.getKey(), entry.getValue());
        }

        jo.put("content_length", curi.isHttpTransaction() && curi.getContentLength() >= 0 ? curi.getContentLength() : JSONObject.NULL);
        jo.put("size", curi.getContentSize() > 0 ? curi.getContentSize() : JSONObject.NULL);

        jo.put("status_code", checkForNull(curi.getFetchStatus()));
        jo.put("url", checkForNull(curi.getUURI().toString()));
        jo.put("hop_path", checkForNull(curi.getPathFromSeed()));
        jo.put("via", checkForNull(curi.flattenVia()));
        jo.put("mimetype", checkForNull(MimetypeUtils.truncate(curi.getContentType())));
        jo.put("thread", checkForNull(curi.getThreadNumber()));

        if (curi.containsDataKey(CoreAttributeConstants.A_FETCH_COMPLETED_TIME)) {
            long beganTime = curi.getFetchBeginTime();
            String fetchBeginDuration = ArchiveUtils.get17DigitDate(beganTime)
                    + "+" + (curi.getFetchCompletedTime() - beganTime);
            jo.put("start_time_plus_duration", fetchBeginDuration);
        } else {
            jo.put("start_time_plus_duration", JSONObject.NULL);
        }

        jo.put("content_digest", checkForNull(curi.getContentDigestSchemeString()));
        jo.put("seed", checkForNull(curi.getSourceTag()));

        CrawlHost host = serverCache.getHostFor(curi.getUURI());
        if (host != null) {
            jo.put("host", host.fixUpName());
        } else {
            jo.put("host", JSONObject.NULL);
        }

        jo.put("annotations", checkForNull(StringUtils.join(curi.getAnnotations(), ",")));

        JSONObject ei = curi.getExtraInfo();
        if (ei == null) {
            ei = new JSONObject();
        }
        // copy so we can remove unrolled fields
        ei = new JSONObject(curi.getExtraInfo().toString());
        ei.remove("contentSize"); // we get this value above
        jo.put("warc_filename", checkForNull(ei.remove("warcFilename")));
        jo.put("warc_offset", checkForNull(ei.remove("warcFileOffset")));
        jo.put("extra_info", ei);

        return jo;
    }

}
