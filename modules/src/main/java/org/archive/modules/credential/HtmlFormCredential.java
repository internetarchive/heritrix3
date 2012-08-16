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

package org.archive.modules.credential;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.lang.StringUtils;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;

/**
 * Credential that holds all needed to do a GET/POST to a HTML form.
 *
 * @author stack
 * @version $Revision$, $Date$
 */
public class HtmlFormCredential extends Credential {
    private static final long serialVersionUID = -4L;

    private static final Logger logger =
        Logger.getLogger(HtmlFormCredential.class.getName());

    /**
     * Full URI of page that contains the HTML login form we're to apply these
     * credentials too: E.g. http://www.archive.org
     */
    protected String loginUri = "";
    public String getLoginUri() {
        return this.loginUri;
    }
    public void setLoginUri(String loginUri) {
        this.loginUri = loginUri;
    }
    
    /**
     * Form items.
     */
    protected Map<String,String> formItems = new HashMap<String,String>();
    public Map<String,String> getFormItems() {
        return this.formItems;
    }
    public void setFormItems(Map<String,String> formItems) {
        this.formItems = formItems;
    }
    
    
    enum Method {
        GET,
        POST
    }
    /**
     * GET or POST.
     */
    protected Method httpMethod = Method.POST;
    public Method getHttpMethod() {
        return this.httpMethod;
    }
    public void setHttpMethod(Method method) {
        this.httpMethod = method; 
    }

    /**
     * Constructor.
     */
    public HtmlFormCredential() {
    }

    public boolean isPrerequisite(final CrawlURI curi) {
        boolean result = false;
        String curiStr = curi.getUURI().toString();
        String loginUri = getPrerequisite(curi);
        if (loginUri != null) {
            try {
                UURI uuri = UURIFactory.getInstance(curi.getUURI(), loginUri);
                if (uuri != null && curiStr != null &&
                    uuri.toString().equals(curiStr)) {
                    result = true;
                    if (!curi.isPrerequisite()) {
                        curi.setPrerequisite(true);
                        logger.fine(curi + " is prereq.");
                    }
                }
            } catch (URIException e) {
                logger.severe("Failed to uuri: " + curi + ", " +
                    e.getMessage());
            }
        }
        return result;
    }

    public boolean hasPrerequisite(CrawlURI curi) {
        return getPrerequisite(curi) != null;
    }

    public String getPrerequisite(CrawlURI curi) {
        return getLoginUri();
    }

    public String getKey() {
        return getLoginUri();
    }

    public boolean isEveryTime() {
        // This authentication is one time only.
        return false;
    }

    @Override
    public boolean populate(CrawlURI curi, HttpClient http, HttpMethod method,
            Map<String, String> httpAuthChallenges) {
        // http is not used
        boolean result = false;
        Map<String,String> formItems = getFormItems();
        if (formItems == null || formItems.size() <= 0) {
            try {
                logger.severe("No form items for " + method.getURI());
            }
            catch (URIException e) {
                logger.severe("No form items and exception getting uri: " +
                    e.getMessage());
            }
            return result;
        }

        NameValuePair[] data = new NameValuePair[formItems.size()];
        int index = 0;
        String key = null;
        for (Iterator<String> i = formItems.keySet().iterator(); i.hasNext();) {
            key = i.next();
            data[index++] = new NameValuePair(key, (String)formItems.get(key));
        }
        if (method instanceof PostMethod) {
            ((PostMethod)method).setRequestBody(data);
            result = true;
        } else if (method instanceof GetMethod) {
            // Append these values to the query string.
            // Get current query string, then add data, then get it again
            // only this time its our data only... then append.
            HttpMethodBase hmb = (HttpMethodBase)method;
            String currentQuery = hmb.getQueryString();
            hmb.setQueryString(data);
            String newQuery = hmb.getQueryString();
            hmb.setQueryString(
                ((StringUtils.isNotEmpty(currentQuery))
                        ? currentQuery + "&"
                        : "")
                + newQuery);
            result = true;
        } else {
            logger.severe("Unknown method type: " + method);
        }
        return result;
    }

    public boolean isPost() {
        return Method.POST.equals(getHttpMethod());
    }
}
