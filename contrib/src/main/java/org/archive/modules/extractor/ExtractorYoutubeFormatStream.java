package org.archive.modules.extractor;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.util.TextUtils;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Youtube stream URI extractor. This will check the content of the youtube
 * watch page looking for the url_encoded_fmt_stream_map json value. The json
 * object is decoded and the stream URIs are constructed and queued.
 * 
 * <pre>
 * {@code
 * <bean id="extractorYoutubeFormatStream" class="org.archive.modules.extractor.ExtractorYoutubeFormatStream">
 *  <property name="enabled" value="false" /> <!-- enable via sheet for http://(com,youtube, -->
 *  <property name="extractLimit" value="1" />
 *  <property name="itagPriority" >
 *   <list>
 *     <value>38</value> <!-- MP4 3072p -->
 *     <value>37</value> <!-- MP4 1080p -->
 *     <value>22</value> <!-- MP4 720p -->
 *     <value>18</value> <!-- MP4 270p/360p -->
 *     <value>35</value> <!-- FLV 480p -->
 *     <value>34</value> <!-- FLV 360p -->
 *   </list>
 *  </property>
 * </bean>
 * }
 * </pre>
 * 
 * @contributor adam
 * @contributor nlevitt
 * 
 */
public class ExtractorYoutubeFormatStream extends Extractor {
    private static Logger logger =
            Logger.getLogger(ExtractorYoutubeFormatStream.class.getName());

    {
        setExtractLimit(1);
    }
    public Integer getExtractLimit(){
        return (Integer) kp.get("extractLimit");
    }
    /**
     * Maximum number of video urls to extract. A value of 0 means extract all
     * discovered video urls. Default is 1.
     */
    public void setExtractLimit(Integer extractLimit){
        kp.put("extractLimit", extractLimit);
    }

    {
        setItagPriority(new ArrayList<String>());
    }
    @SuppressWarnings("unchecked")
    public List<String> getItagPriority() {
        return (List<String>) kp.get("itagPriority");
    }

    /**
     * Itag priority list. Youtube itag parameter specifies the video and audio
     * format and quality. The default is an empty list, which tells the
     * extractor to extract up to extractLimit video urls. When the list is not
     * empty, only video urls with itag values in the list are extracted.
     * 
     * @see <a
     *      href="http://en.wikipedia.org/wiki/YouTube">http://en.wikipedia.org/wiki/YouTube</a>
     */
    public void setItagPriority(List<String> itagPriority) {
        kp.put("itagPriority", itagPriority);
    }

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        return uri.getContentLength() > 0
                && uri.getFetchStatus() == 200
                && TextUtils.matches("^https?://([^.]+\\.)?youtube\\.com/watch.*$", 
                        uri.getUURI().toCustomString());
    }

    @Override
    protected void extract(CrawlURI uri) {
        ReplayCharSequence cs;
        try {
            cs = uri.getRecorder().getContentReplayCharSequence();
        } catch (IOException e) {
            uri.getNonFatalFailures().add(e);
            logger.log(Level.WARNING, "Failed get of replay char sequence in "
                    + Thread.currentThread().getName(), e);
            return;
        }

        Matcher matcher = TextUtils.getMatcher(
                "(?is)ytplayer\\.config = (\\{.*?\\})(;|</script>|$)", cs);
        if (matcher.find()) {
            String jsonStr = matcher.group(1);

            // logger.fine("Just Extracted: "+jsonStr);
            try {
                JSONObject json = new JSONObject(jsonStr);
                if (json.has("args")) {
                    JSONObject args = json.getJSONObject("args");
                    if (args.has("url_encoded_fmt_stream_map")) {
                        String streamMap = args.getString("url_encoded_fmt_stream_map");

                        // logger.info("Just Extracted: "+stream_map);
                        LinkedHashMap<String, String> parsedVideoMap = parseStreamMap(streamMap);
                        addPreferredOutlinks(uri, parsedVideoMap);
                    }
                }
            } catch (JSONException e) {
                logger.log(Level.WARNING,
                        "Error parsing JSON object - Skipping extraction", e);
            }
        }
        TextUtils.recycleMatcher(matcher);
    }

    // 34 and 35 are most common medium quality flvs, others are in arbitrary order
    private static final List<String> DEFAULT_ITAG_PRIORITY = Arrays.asList(
            "35", "34", "5", "6", "13", "17", "18", "22", "36", "37", "38",
            "43", "44", "45", "46", "82", "83", "84", "85", "100", "101",
            "102", "120");
    private static final Set<String> KNOWN_ITAGS = new HashSet<String>(DEFAULT_ITAG_PRIORITY);

    // Add videos as outlinks by priority list
    private void addPreferredOutlinks(CrawlURI uri,
            LinkedHashMap<String, String> parsedVideoMap) {
        List<String> itagPriority;
        if (getItagPriority() != null && !getItagPriority().isEmpty()) {
            itagPriority = getItagPriority();
        } else {
            itagPriority = DEFAULT_ITAG_PRIORITY;
        }

        int extractionCount = 0;
        for (String itag : itagPriority) {
            if (parsedVideoMap.containsKey(itag)
                    && (getExtractLimit() <= 0 || extractionCount < getExtractLimit())) {
                logger.fine("adding video: " + parsedVideoMap.get(itag));
                addOutlink(uri, parsedVideoMap.get(itag),
                        org.archive.modules.extractor.LinkContext.EMBED_MISC,
                        org.archive.modules.extractor.Hop.EMBED);
                extractionCount++;
            }
        }

        // if itagPriority not specified, make sure we consider all discovered
        // video urls
        if (getItagPriority() == null || getItagPriority().isEmpty()) {
            Iterator<String> itagKeyIter = parsedVideoMap.keySet().iterator();
            while (itagKeyIter.hasNext() && (getExtractLimit() <= 0 || extractionCount < getExtractLimit())) {
                String itag = itagKeyIter.next();
                if (!KNOWN_ITAGS.contains(itag)) {
                    logger.warning("adding video (with unknown itag " + itag
                            + "): " + parsedVideoMap.get(itag));
                    addOutlink(uri, parsedVideoMap.get(itag),
                            org.archive.modules.extractor.LinkContext.EMBED_MISC,
                            org.archive.modules.extractor.Hop.EMBED);
                    extractionCount++;
                }
            }
        }
    }

    private LinkedHashMap<String, String> parseStreamMap(String streamMap) {
        String[] rawVideoList = streamMap.split(",");
        LinkedHashMap<String, String> parsedVideoMap = new LinkedHashMap<String, String>();

        // Parse Video Map into itag,url pair
        for (int i = 0; i < rawVideoList.length; i++) {
            String[] videoParams = rawVideoList[i].split("\\u0026");
            String videoURLParam, itagParam, sigParam;
            videoURLParam = itagParam = sigParam = "";

            for (String param : videoParams) {

                String[] keyValuePair = param.split("=");
                if (keyValuePair.length != 2) {
                    logger.warning("Invalid Video Parameter: " + param);
                    continue;
                }

                if (keyValuePair[0].equals("url")) {
                    videoURLParam = keyValuePair[1];
                }
                if (keyValuePair[0].equals("itag")) {
                    itagParam = keyValuePair[1];
                }
                if (keyValuePair[0].equals("sig")) {
                    sigParam = keyValuePair[1];
                }
            }

            if (videoURLParam.length() > 0 && itagParam.length() > 0
                    && sigParam.length() > 0) {
                try {
                    String fixupURL = URLDecoder.decode(videoURLParam
                            + "%26signature=" + sigParam, "UTF-8");
                    parsedVideoMap.put(itagParam, fixupURL);
                } catch (java.io.UnsupportedEncodingException e) {
                    logger.warning("Error decoding youtube video URL: "
                            + videoURLParam + "%26signature=" + sigParam);
                }
            }
        }
        return parsedVideoMap;
    }

}