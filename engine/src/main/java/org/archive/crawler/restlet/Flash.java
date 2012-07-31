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

package org.archive.crawler.restlet;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.restlet.data.Cookie;
import org.restlet.data.CookieSetting;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.util.Series;

/**
 * Utility for including a brief last-action or background-action 
 * message on web responses. 
 * 
 * @contributor gojomo
 *
 */
public class Flash {
    /** usual types */
    public enum Kind {ACK, NACK, ADVISORY}
    
    protected static long nextdrop = RandomUtils.nextLong();
    protected static Map<Long,Flash> dropboxes = new LinkedHashMap<Long, Flash>() {
        private static final long serialVersionUID = 1L;
        @Override
        protected boolean removeEldestEntry(Entry<Long, Flash> eldest) {
            return size()>100;
        }
        
    };
    
    public static void addFlash(Response response, String message) {
        addFlash(response, message, Kind.ACK);
    }
    
    public static void addFlash(Response response, String message, Kind kind) {
        dropboxes.put(nextdrop,new Flash(message, kind));
        Series<CookieSetting> cookies = response.getCookieSettings();
        CookieSetting flashdrop = null; 
        for(CookieSetting cs : cookies) {
            if(cs.getName().equals("flashdrop")) {
                flashdrop = cs; 
            }
        }
        if(flashdrop == null) {
            cookies.add(new CookieSetting("flashdrop",Long.toString(nextdrop)));
        } else {
            flashdrop.setValue(flashdrop.getValue()+","+Long.toString(nextdrop));
        }
        nextdrop++;
    }
    
    public static List<Flash> getFlashes(Request request) {
        List<Flash> flashes = new LinkedList<Flash>();
        Series<Cookie> cookies = request.getCookies();
        String flashdrops = cookies.getFirstValue("flashdrop");
        if (StringUtils.isBlank(flashdrops)) {
            return flashes;
        }
        for (String dropbox : flashdrops.split(",")) {
            if(dropbox!=null) {
                Flash flash = dropboxes.remove(Long.parseLong(dropbox));
                if(flash!=null) {
                    flashes.add(flash); 
                }
            }
        }
        return flashes;
    }
    
    public static void renderFlashesHTML(Writer writer, Request request) {
        PrintWriter pw = new PrintWriter(writer); 
        for(Flash flash : getFlashes(request)) {
            pw.println("<div class='flash"+flash.getKind()+"'>");
            pw.println(flash.getMessage());
            pw.println("</div>");
        }
        pw.flush();
    }
    
    
    /** kind of flash, ACK NACK or ADVISORY */
    protected Kind kind;
    /** the message to show, if any  */
    protected String message;
    
    /**
     * Create an ACK flash of default styling with the given message.
     * 
     * @param message
     */
    public Flash(String message) {
        this(message, Kind.ACK);
    }
    
    /**
     * Create a Flash of the given kind, message with default styling.
     * 
     * @param kind
     * @param message
     */
    public Flash(String message, Kind kind) {
        this.kind = kind;
        this.message = message; 
    }
    
    /**
     * Indicate whether the Flash should persist. The usual and 
     * default case is that a Flash displays once and then expires.
     * 
     * @return boolean whether to discard Flash
     */
    public boolean isExpired() {
        return true;
    }

    public String getMessage() {
        return this.message;
    }

    public Kind getKind() {
        return this.kind;
    }
}
