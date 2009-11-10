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

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.util.Map;
import java.util.SortedMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.Cookie;
import org.archive.spring.ConfigPath;
import org.archive.util.IoUtils;
import org.springframework.context.Lifecycle;

/**
 * @author pjack
 *
 */
public abstract class AbstractCookieStorage 
    implements CookieStorage, 
               Lifecycle, // InitializingBean, 
               Closeable, 
               Serializable {

    final private static Logger LOGGER = 
        Logger.getLogger(AbstractCookieStorage.class.getName());
    
    protected ConfigPath cookiesLoadFile = null;
    public ConfigPath getCookiesLoadFile() {
        return cookiesLoadFile;
    }
    public void setCookiesLoadFile(ConfigPath cookiesLoadFile) {
        this.cookiesLoadFile = cookiesLoadFile;
    }

    
    protected ConfigPath cookiesSaveFile = null;
    public ConfigPath getCookiesSaveFile() {
        return cookiesSaveFile;
    }
    public void setCookiesSaveFile(ConfigPath cookiesSaveFile) {
        this.cookiesSaveFile = cookiesSaveFile;
    }

    boolean isRunning = false; 
    public void start() {
        if(isRunning()) {
            return;
        }
        SortedMap<String,Cookie> cookies = prepareMap();
        if (getCookiesLoadFile()!=null) {
            loadCookies(getCookiesLoadFile().getFile().getAbsolutePath(), cookies);
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
     * Load cookies from a file before the first fetch.
     * <p>
     * The file is a text file in the Netscape's 'cookies.txt' file format.<br>
     * Example entry of cookies.txt file:<br>
     * <br>
     * www.archive.org FALSE / FALSE 1074567117 details-visit texts-cralond<br>
     * <br>
     * Each line has 7 tab-separated fields:<br>
     * <li>1. DOMAIN: The domain that created and have access to the cookie
     * value.
     * <li>2. FLAG: A TRUE or FALSE value indicating if hosts within the given
     * domain can access the cookie value.
     * <li>3. PATH: The path within the domain that the cookie value is valid
     * for.
     * <li>4. SECURE: A TRUE or FALSE value indicating if to use a secure
     * connection to access the cookie value.
     * <li>5. EXPIRATION: The expiration time of the cookie value (unix style.)
     * <li>6. NAME: The name of the cookie value
     * <li>7. VALUE: The cookie value
     * 
     * @param cookiesFile
     *            file in the Netscape's 'cookies.txt' format.
     */
    public static void loadCookies(String cookiesFile, 
            SortedMap<String,Cookie> result) {

        // Do nothing if cookiesFile is not specified.
        if (cookiesFile == null || cookiesFile.length() <= 0) {
            return;
        }
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(cookiesFile, "r");
            String[] cookieParts;
            String line;
            Cookie cookie = null;
            while ((line = raf.readLine()) != null) {
                // Line that starts with # is commented line, therefore skip it.
                if (!line.startsWith("#")) {
                    cookieParts = line.split("\\t");
                    if (cookieParts.length == 7) {
                        // Create cookie with not expiration date (-1 value).
                        // TODO: add this as an option.
                        cookie = new Cookie(cookieParts[0], cookieParts[5],
                                cookieParts[6], cookieParts[2], -1, Boolean
                                        .valueOf(cookieParts[3]).booleanValue());

                        if (cookieParts[1].toLowerCase().equals("true")) {
                            cookie.setDomainAttributeSpecified(true);
                        } else {
                            cookie.setDomainAttributeSpecified(false);
                        }
                        
                        LOGGER.fine("Adding cookie: "
                                        + cookie.toExternalForm());
                    }
                }
            }
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.WARNING,"Could not find file: " + cookiesFile, e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,e.getMessage(), e);
        } finally {
            IoUtils.close(raf);
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
            out.write( "# This file is the Netscape cookies.txt format\n\n".getBytes()); 
            for (Cookie cookie: cookies.values()) { 
                MutableString line = new MutableString(1024 * 2); 
                // Guess an initial size 
                line.append(cookie.getDomain()); 
                line.append(tab);
                line.append(cookie.isDomainAttributeSpecified() == true ? "TRUE" : "FALSE"); 
                line.append(tab); 
                line.append(cookie.getPath());
                line.append(tab); 
                line.append(cookie.getSecure() == true ? "TRUE" : "FALSE"); 
                line.append(tab); 
                line.append(cookie.getName());
                line.append(tab);                
                line.append((null==cookie.getValue())?"":cookie.getValue()); 
                line.append("\n");
                out.write(line.toString().getBytes()); 
            } 
        } catch (FileNotFoundException e) { 
            // We should probably throw FatalConfigurationException.
            System.out.println("Could not find file: " + saveCookiesFile); 
        } catch (IOException e) {
            e.printStackTrace(); 
        } finally { 
            try { 
                if (out != null) { 
                    out.close(); 
                } 
            } catch (IOException e) { 
                e.printStackTrace(); 
            } 
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
