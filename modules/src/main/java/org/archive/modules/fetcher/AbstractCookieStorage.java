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

package org.archive.modules.fetcher;

import it.unimi.dsi.mg4j.util.MutableString;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Date;
import java.util.Map;
import java.util.SortedMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.io.IOUtils;
import org.archive.spring.ConfigFile;
import org.archive.spring.ConfigPath;
import org.springframework.context.Lifecycle;

/**
 * @author pjack
 *
 */
public abstract class AbstractCookieStorage 
    implements CookieStorage, 
               Lifecycle, // InitializingBean, 
               Closeable {

    final private static Logger LOGGER = 
        Logger.getLogger(AbstractCookieStorage.class.getName());
    
    protected ConfigFile cookiesLoadFile = null;
    public ConfigFile getCookiesLoadFile() {
        return cookiesLoadFile;
    }
    public void setCookiesLoadFile(ConfigFile cookiesLoadFile) {
        this.cookiesLoadFile = cookiesLoadFile;
    }

    
    protected ConfigPath cookiesSaveFile = null;
    public ConfigPath getCookiesSaveFile() {
        return cookiesSaveFile;
    }
    public void setCookiesSaveFile(ConfigPath cookiesSaveFile) {
        this.cookiesSaveFile = cookiesSaveFile;
    }

    protected boolean isRunning = false; 
    public void start() {
        if(isRunning()) {
            return;
        }
        SortedMap<String,Cookie> cookies = prepareMap();
        if (getCookiesLoadFile()!=null) {
            loadCookies(getCookiesLoadFile(), cookies);
        }
        isRunning = true; 
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public void stop() {
        isRunning = false; 
    }

    protected abstract SortedMap<String,Cookie> prepareMap();
    
    /**
     * Load cookies. The input is text in the Netscape's 'cookies.txt' file
     * format. Example entry of cookies.txt file:
     * <p>
     * www.archive.org FALSE / FALSE 1311699995 details-visit texts-cralond
     * </p>
     * <p>
     * Each line has 7 tab-separated fields:
     * </p>
     * <ol>
     * <li>DOMAIN: The domain that created and have access to the cookie value.</li>
     * <li>FLAG: A TRUE or FALSE value indicating if hosts within the given
     * domain can access the cookie value.</li>
     * <li>PATH: The path within the domain that the cookie value is valid for.</li>
     * <li>SECURE: A TRUE or FALSE value indicating if to use a secure
     * connection to access the cookie value.</li>
     * <li>EXPIRATION: The expiration time of the cookie value, or -1 for no
     * expiration</li>
     * <li>NAME: The name of the cookie value</li>
     * <li>VALUE: The cookie value</li>
     * </ol>
     * 
     * @param reader
     *            input
     * @param cookiesFile
     *            file in the Netscape's 'cookies.txt' format.
     */
    public static void loadCookies(Reader reader,
            SortedMap<String, Cookie> cookies) {
        BufferedReader br = new BufferedReader(reader);
        try {
            String line;
            int lineNo = 1;
            while ((line = br.readLine()) != null) {
                if (!line.matches("\\s*(?:#.*)?")) { // skip blank links and comments
                    String[] tokens = line.split("\\t");
                    if (tokens.length == 7) {
                        long epochSeconds = Long.parseLong(tokens[4]);
                        Date expirationDate = (epochSeconds >= 0 ? new Date(epochSeconds * 1000) : null);
                        Cookie cookie = new Cookie(tokens[0], tokens[5],
                                tokens[6], tokens[2], expirationDate, 
                                Boolean.valueOf(tokens[3]).booleanValue());
                        cookie.setDomainAttributeSpecified(Boolean.valueOf(tokens[1]).booleanValue());
                        
                        LOGGER.fine("Adding cookie: domain " + cookie.getDomain() + " cookie " + cookie.toExternalForm());
                        cookies.put(cookie.getSortKey(), cookie);
                    } else {
                        LOGGER.warning("cookies input line " + lineNo + " invalid, expected 7 tab-delimited tokens");
                    }
                }
                
                lineNo++;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,e.getMessage(), e);
        }
    }

    protected static void loadCookies(ConfigFile file,
            SortedMap<String, Cookie> cookies) {
        
        Reader reader = null;
        try {
            reader = file.obtainReader();
            loadCookies(reader, cookies);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    public static void loadCookies(String cookiesFile, 
            SortedMap<String,Cookie> result) {

        // Do nothing if cookiesFile is not specified.
        if (cookiesFile == null || cookiesFile.length() <= 0) {
            return;
        }
        
        FileReader reader = null;
        try {
            reader = new FileReader(cookiesFile);
            loadCookies(reader, result);
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.WARNING,"Could not find file: " + cookiesFile, e);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }
    
    public static void saveCookies(String saveCookiesFile, Map<String,Cookie> cookies) { 
        // Do nothing if cookiesFile is not specified. 
        if (saveCookiesFile == null || saveCookiesFile.length() <= 0) { 
            return; 
        }
      
        FileOutputStream out = null; 
        try { 
            out = new FileOutputStream(new File(saveCookiesFile)); 
            String tab ="\t"; 
            out.write("# Heritrix Cookie File\n".getBytes()); 
            out.write("# This file is the Netscape cookies.txt format\n\n".getBytes()); 
            for (Cookie cookie: cookies.values()) { 
                // Guess an initial size 
                MutableString line = new MutableString(1024 * 2); 
                line.append(cookie.getDomain()); 
                line.append(tab);
                line.append(cookie.isDomainAttributeSpecified() ? "TRUE" : "FALSE"); 
                line.append(tab); 
                line.append(cookie.getPath());
                line.append(tab); 
                line.append(cookie.getSecure() ? "TRUE" : "FALSE"); 
                line.append(tab);
                line.append(cookie.getExpiryDate() != null ? cookie.getExpiryDate().getTime() / 1000 : -1);
                line.append(tab);
                line.append(cookie.getName());
                line.append(tab);                
                line.append(cookie.getValue() != null ? cookie.getValue() : ""); 
                line.append("\n");
                out.write(line.toString().getBytes()); 
            } 
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to write " + saveCookiesFile, e);
        } finally {
            IOUtils.closeQuietly(out);
        } 
    }

    public abstract SortedMap<String,Cookie> getCookiesMap();

    public void saveCookiesMap(Map<String, Cookie> map) {
        innerSaveCookiesMap(map);
        if (getCookiesSaveFile()!=null) {
            saveCookies(getCookiesSaveFile().getFile().getAbsolutePath(), map);
        }
    }
    
    protected abstract void innerSaveCookiesMap(Map<String,Cookie> map);

    public void close() throws IOException {
    }

}
