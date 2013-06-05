package org.archive.modules.extractor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.httpclient.URIException;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.modules.net.RobotsPolicy;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.DevUtils;
import org.archive.util.TextUtils;
import org.archive.util.UriUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Youtube stream URI extractor.
 * This will check the content of the youtube watch page looking for the url_encoded_fmt_stream_map json value.
 * The json object is decoded and the stream URIs are constructed and queued.
 * 
 * @contributor adam
 *
 */
public class ExtractorYoutubeFormatStream extends ContentExtractor {
	private static Logger logger =
            Logger.getLogger(ExtractorYoutubeFormatStream.class.getName());

    /**
     * Maximum number of videos to extract
     */
    {
    	setExtractLimit(1);
    }
    public Integer getExtractLimit(){
    	return (Integer) kp.get("extractLimit");
    }
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
    public void setItagPriority(List<String> itagPriority) {
        kp.put("itagPriority", itagPriority);
    }

	@Override
	protected boolean shouldExtract(CrawlURI uri){
	    return true;
	    // if (uri.getFetchStatus() != 200) {
	    // 	//verify we are a youtube watch page
	    // 	return false;
	    // }
	    // else {
	    // 	return true;
     //    }                                                                                                                                                                                                  
    }
    @Override
    protected boolean innerExtract(CrawlURI uri) {
		ReplayCharSequence cs;
        try {
            cs = uri.getRecorder().getContentReplayCharSequence();
        } catch (IOException e) {
            uri.getNonFatalFailures().add(e);
            logger.log(Level.WARNING,"Failed get of replay char sequence in " +
                Thread.currentThread().getName(), e);
            return false;
        }
        Matcher matcher = TextUtils.getMatcher("(?is)ytplayer.config = ([^;]*);", cs);
        if (matcher.find()) {                                                                                                                                                                          
            String jsonStr = matcher.group(1);                                                                                                                                                           
            
            logger.info("Just Extracted: "+jsonStr);
            JSONObject json;
            try {                                                                                                                                                                                        
            json = new JSONObject(jsonStr);
                if(json.has("args")){                                                                                                                                                                      
                	JSONObject args = json.getJSONObject("args");
                	if(args.has("url_encoded_fmt_stream_map")) {
						String stream_map = args.getString("url_encoded_fmt_stream_map");

						logger.info("Just Extracted: "+stream_map);

						String[] videos = stream_map.split(",");

						for(int i=0; i < videos.length; i++) {
							String[] videoParams = videos[i].split("\u0026");

							Matcher videoMatchPre = TextUtils.getMatcher("(?is)sig=([^&]*).*url=(http[^&]*)", videos[i]);
							Matcher videoMatchPost = TextUtils.getMatcher("(?is)url=(http[^&]*).*sig=([^&]*)", videos[i]);
							String videoUri="";
							String prefix,suffix;

							if(videoMatchPre.find()) {
								prefix = videoMatchPre.group(2);
								suffix = videoMatchPre.group(1);
							}
							else if(videoMatchPost.find()) {
								prefix = videoMatchPost.group(1);
								suffix = videoMatchPost.group(2);
							}                        
							else
							   return false;

							try {
								videoUri = new java.net.URI(prefix+"%26signature="+suffix).getPath();
							}
							catch(java.net.URISyntaxException e) {
								logger.warning("problem decoding link " + prefix+"%26signature="+suffix + " - " + e);
							}

							TextUtils.recycleMatcher(videoMatchPre);
							TextUtils.recycleMatcher(videoMatchPost);


							logger.info("found video: "+videoUri);
							int max = getExtractorParameters().getMaxOutlinks();
							String gen204 = videoUri.substring(0,videoUri.lastIndexOf("/"))+"/generate_204";

							addOutlink(uri,gen204.toString(), org.archive.modules.extractor.LinkContext.EMBED_MISC, org.archive.modules.extractor.Hop.EMBED);

							logger.warning("creating gen204: "+gen204);
							logger.warning("newOutlinkCount: "+numberOfLinksExtracted.incrementAndGet());

							addOutlink(uri,videoUri.toString(), org.archive.modules.extractor.LinkContext.EMBED_MISC, org.archive.modules.extractor.Hop.EMBED);

							logger.warning("adding video: "+videoUri);
							logger.warning("newOutlinkCount: "+numberOfLinksExtracted.incrementAndGet());

						} 
                	}
                }
            }
            catch(JSONException e) {                                                                                                                                                                     
                 logger.log(Level.WARNING, "Error parsing JSON object - Skipping: "+jsonStr, e);
               }
        }
        TextUtils.recycleMatcher(matcher);
        return true;
    }


}