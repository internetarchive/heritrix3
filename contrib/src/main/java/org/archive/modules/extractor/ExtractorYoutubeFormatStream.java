/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.archive.modules.extractor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
 *     <value>38</value> <!-- MP4 3072p (Discontinued) -->
 *     <value>37</value> <!-- MP4 1080p (Discontinued) -->
 *     <value>22</value> <!-- MP4 720p -->
 *     <value>18</value> <!-- MP4 270p/360p -->
 *     <value>35</value> <!-- FLV 480p (Discontinued) -->
 *     <value>34</value> <!-- FLV 360p (Discontinued) -->
 *     <value>36</value> <!-- 3GP 240p -->
 *     <value>5</value> <!-- FLV 240p -->
 *     <value>17</value> <!-- 3GP 144p -->
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

    /*
     * itag information from "Comparison of YouTube media encoding options" at
     * http://en.wikipedia.org/w/index.php?title=YouTube&oldid=596563824#
     * Quality_and_codecs
     */
    private static final List<String> DEFAULT_ITAG_PRIORITY = Arrays.asList(
            /*
             * Prioritize traditional streams that include video+audio in order of quality.
             * Some are marked as discontinued according to wikipedia (does that mean youtube will
             * never have them anymore?)
             * 
             */

            /*
             * Highest quality traditional stream.
             */
            "37",   // (discontinued) MP4   1080p   H.264   High    3–5.9   AAC     192 

            /*
             * Traditional streams that are currently in use
             */
            "22",   // MP4  720p    H.264   High    2-3     AAC     192 
            "43",   // WebM 360p    VP8     N/A     0.5     Vorbis  128 
            "18",   // MP4  270p/360p       H.264   Baseline        0.5     AAC     96  
            "5",    // FLV  240p    Sorenson H.263  N/A     0.25    MP3     64  
            "36",   // 3GP  240p    MPEG-4 Visual   Simple  0.175   AAC     36  
            "17",   // 3GP  144p    MPEG-4 Visual   Simple  0.05    AAC     24

            /*
             * Discontinued FLV Standard Def
             */
            "35",   // (discontinued) FLV   480p    H.264   Main    0.8-1   AAC     128 
            "34",   // (discontinued) FLV   360p    H.264   Main    0.5     AAC     128 

            /*
             * 3D  streams that include video+audio
             */
            "85",   // MP4  1080p   H.264   3D      3-4     AAC     192 
            "84",   // MP4  720p    H.264   3D      2-3     AAC     192 
            "100",  // WebM 360p    VP8     3D      N/A     Vorbis  128 
            "82",   // MP4  360p    H.264   3D      0.5     AAC     96  
            "83",   // MP4  240p    H.264   3D      0.5     AAC     96  

            /*
             * Discontinued (but maybe not completely phased out?)
             */
            "6",    // (discontinued) FLV   270p    Sorenson H.263  N/A     0.8     MP3     64  
            "13",   // (discontinued) 3GP   N/A     MPEG-4 Visual   N/A     0.5     AAC     N/A 
            "38",   // (discontinued) MP4   3072p   H.264   High    3.5-5   AAC     192 
            "44",   // (discontinued) WebM  480p    VP8     N/A     1       Vorbis  128 
            "45",   // (discontinued) WebM  720p    VP8     N/A     2       Vorbis  192 
            "46",   // (discontinued) WebM  1080p   VP8     N/A     N/A     Vorbis  192 
            "101",  // (discontinued) WebM  360p    VP8     3D      N/A     Vorbis  192 
            "102",  // (discontinued) WebM  720p    VP8     3D      N/A     Vorbis  192

            /*
             * live streaming - not sure what happens if we try to download one
             * of these
             */
            "95",   // (live streaming) MP4 720p    H.264   Main    1.5-3   AAC     256     
            "96",   // (live streaming) MP4 1080p   H.264   High    2.5-6   AAC     256     
            "94",   // (live streaming) MP4 480p    H.264   Main    0.8-1.25        AAC     128     
            "93",   // (live streaming) MP4 360p    H.264   Main    0.5-1   AAC     128     
            "92",   // (live streaming) MP4 240p    H.264   Main    0.15-0.3        AAC     48
            "132",  // (live streaming) MP4 240p    H.264   Baseline        0.15-0.2        AAC     48
            "151",  // (live streaming) MP4 72p     H.264   Baseline        0.05    AAC     24

            /*
             * http://en.wikipedia.org/wiki/Dynamic_Adaptive_Streaming_over_HTTP
             * separate video and audio streams, not preferred because we
             * haven't even begun to look at playback
             */
            "136",  // (MPEG-DASH video only) MP4   720p    H.264   Main    1-1.5
            "137",  // (MPEG-DASH video only) MP4   1080p   H.264   High    2-3
            "135",  // (MPEG-DASH video only) MP4   480p    H.264   Main    0.5-1
            "264",  // (MPEG-DASH video only) MP4   1440p   H.264   High    4-5
            "134",  // (MPEG-DASH video only) MP4   360p    H.264   Main    0.3-0.4
            "133",  // (MPEG-DASH video only) MP4   240p    H.264   Main    0.2-0.3
            "160",  // (MPEG-DASH video only) MP4   144p    H.264   Main    0.1
            "172",  // (MPEG-DASH audio only) WebM  Vorbis  192
            "140",  // (MPEG-DASH audio only) MP4   AAC     128
            "171",  // (MPEG-DASH audio only) WebM  Vorbis  128
            "120",  // (discontinued; live streaming) FLV   720p    H.264   Main@L3.1       2       AAC     128
            "141",  // (discontinued; MPEG-DASH audio only) MP4     AAC     256
            "139"   // (discontinued; MPEG-DASH audio only) MP4     AAC     48
            );

    private static final Set<String> KNOWN_ITAGS = new HashSet<String>(DEFAULT_ITAG_PRIORITY);

    // Add videos as outlinks by priority list
    protected void addPreferredOutlinks(CrawlURI uri,
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

    protected LinkedHashMap<String, String> parseStreamMap(String streamMap) {
        String[] rawVideoList = streamMap.split(",");
        LinkedHashMap<String, String> parsedVideoMap = new LinkedHashMap<String, String>();

        // Parse Video Map into itag,url pair
        for (int i = 0; i < rawVideoList.length; i++) {
            String[] videoParams = rawVideoList[i].split("\\u0026");
            String videoURLParam=null, itagParam=null, sigParam=null;

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
                if (keyValuePair[0].equals("sig") || keyValuePair[0].equals("s")) {
                    sigParam = keyValuePair[1];
                }
            }

            if (videoURLParam != null && itagParam != null) {
                try {
                    String fixupURL = URLDecoder.decode(videoURLParam, "UTF-8");
                    if (sigParam != null) {
                        fixupURL = fixupURL + "&signature=" + sigParam;
                    }
                    if (!fixupURL.contains("signature=")) {
                        logger.warning("no 'signature' parameter in raw url or in stream map: " + fixupURL);
                    }
                    parsedVideoMap.put(itagParam, fixupURL);
                } catch (UnsupportedEncodingException e) {
                    logger.warning("Error decoding youtube video URL: "
                            + videoURLParam);
                }
            }
        }
        return parsedVideoMap;
    }

}