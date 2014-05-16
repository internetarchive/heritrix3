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
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.lang.StringEscapeUtils;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.util.TextUtils;
import org.json.JSONException;
import org.json.JSONObject;

public class ExtractorYoutubeChannelFormatStream extends
        ExtractorYoutubeFormatStream {
    
    private static Logger logger =
            Logger.getLogger(ExtractorYoutubeChannelFormatStream.class.getName());

    {
        setExtractLimit(1);
    }
    
    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        return uri.getContentLength() > 0
                && uri.getFetchStatus() == 200
                && TextUtils.matches("^https?://(?:www\\.)?youtube\\.com/user.*$", 
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
                "data-swf-config=\"(\\{.*?\\}\")>", cs);
        
        if (matcher.find()) {
             String str = matcher.group(1);

             String jsonStr = StringEscapeUtils.unescapeHtml(StringEscapeUtils.unescapeHtml(str));
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
    

}

