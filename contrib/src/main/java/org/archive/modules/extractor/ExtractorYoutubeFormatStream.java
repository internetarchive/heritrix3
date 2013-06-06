package org.archive.modules.extractor;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.util.TextUtils;
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
public class ExtractorYoutubeFormatStream extends Extractor {
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
    protected boolean shouldProcess(CrawlURI uri) {
        if (uri.getContentLength() <= 0) {
            return false;
        }
        if (uri.getFetchStatus() != 200) {
        	//TODO: verify we are a youtube watch page
        	return false;
        }
        return true;
    }

    @Override
    protected void extract(CrawlURI uri) {
		ReplayCharSequence cs;
        try {
            cs = uri.getRecorder().getContentReplayCharSequence();
        } catch (IOException e) {
            uri.getNonFatalFailures().add(e);
            logger.log(Level.WARNING,"Failed get of replay char sequence in " +
                Thread.currentThread().getName(), e);
            return;
        }
        Matcher matcher = TextUtils.getMatcher("(?is)ytplayer.config = ([^;]*);", cs);
        if (matcher.find()) {                                                                                                                                                                          
            String jsonStr = matcher.group(1);                                                                                                                                                           
            
            //logger.fine("Just Extracted: "+jsonStr);
            JSONObject json;
            try {                                                                                                                                                                                        
            json = new JSONObject(jsonStr);
                if(json.has("args")){                                                                                                                                                                      
                	JSONObject args = json.getJSONObject("args");
                	if(args.has("url_encoded_fmt_stream_map")) {
						String stream_map = args.getString("url_encoded_fmt_stream_map");

						//logger.info("Just Extracted: "+stream_map);

						String[] rawVideoList = stream_map.split(",");
						LinkedHashMap<String,String> parsedVideoMap = new LinkedHashMap<String,String>();

						//Parse Video Map into itag,url pair
						for(int i=0; i < rawVideoList.length; i++) {
							String[] videoParams = rawVideoList[i].split("\\u0026");
							String videoURLParam, itagParam, sigParam;
							videoURLParam = itagParam = sigParam = "";

							for(String param : videoParams){
								
								String[] keyValuePair = param.split("=");
								if (keyValuePair.length != 2) {
									logger.warning("Invalid Video Parameter: "
											+ param);
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

							if(videoURLParam.length()>0 && itagParam.length()>0 && sigParam.length()>0) {
								try {
									String fixupURL = URLDecoder.decode(videoURLParam+"%26signature="+sigParam, "UTF-8");
									parsedVideoMap.put(itagParam,fixupURL);
								}
								catch(java.io.UnsupportedEncodingException e) {
									logger.warning("Error decoding youtube video URL: "+videoURLParam+"%26signature="+sigParam);
								}
							}
						}
						// for(String itag : getItagPriority()){
						// 	logger.warning("itag Priority List has: "+itag);
						// }
						// for(String itag : parsedVideoMap.keySet()) {
						// 	logger.warning("parsed List has itag: "+itag);
						// }
						//Add videos as outlinks by priority list
						int extractionCount=0;
						for(String itag : getItagPriority()) {
							if(parsedVideoMap.containsKey(itag) && extractionCount<getExtractLimit()) {
								int hostnameEndIndex = parsedVideoMap.get(itag).lastIndexOf("/");
								if(hostnameEndIndex<1)
									continue;
								String gen204 = parsedVideoMap.get(itag).substring(0,hostnameEndIndex)+"/generate_204";

								logger.warning("creating gen204: "+gen204);
								addOutlink(uri,gen204.toString(), org.archive.modules.extractor.LinkContext.EMBED_MISC, org.archive.modules.extractor.Hop.EMBED);
								logger.warning("adding video: "+parsedVideoMap.get(itag));
								addOutlink(uri,parsedVideoMap.get(itag), org.archive.modules.extractor.LinkContext.EMBED_MISC, org.archive.modules.extractor.Hop.EMBED);
								extractionCount++;
							}
						}
                	}
                }
            }
            catch(JSONException e) {                                                                                                                                                                     
                 logger.log(Level.WARNING, "Error parsing JSON object - Skipping: "+jsonStr, e);
               }
        }
        TextUtils.recycleMatcher(matcher);
    }


}