package org.archive.modules.credential;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.lang.StringUtils;
import org.archive.modules.CrawlURI;

public class CommonsHttpCredentialUtil {

    private static Logger logger = Logger.getLogger(CommonsHttpCredentialUtil.class.getName());

    public static boolean populate(CrawlURI curi, HttpClient http,
            HttpMethod method, Credential cred) {
        if (cred instanceof HttpAuthenticationCredential) {
            return populate(curi, http, method, (HttpAuthenticationCredential) cred);
        } else if (cred instanceof HtmlFormCredential) {
            return populate(curi, http, method, (HtmlFormCredential) cred);
        } else {
            throw new RuntimeException("not implemented for Credential subtype " + cred.getClass());
        }
    }

    public static boolean populate(CrawlURI curi, HttpClient http,
            HttpMethod method, HtmlFormCredential cred) {
        // http is not used
        boolean result = false;
        Map<String,String> formItems = cred.getFormItems();
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

    public static boolean populate(CrawlURI curi, HttpClient http,
            HttpMethod method, HttpAuthenticationCredential cred) {
        boolean result = false;

        // Always add the credential to HttpState. Doing this because no way of
        // removing the credential once added AND there is a bug in the
        // credentials management system in that it always sets URI root to
        // null: it means the key used to find a credential is NOT realm + root
        // URI but just the realm. Unless I set it everytime, there is
        // possibility that as this thread progresses, it might come across a
        // realm already loaded but the login and password are from another
        // server. We'll get a failed authentication that'd be difficult to
        // explain.
        //
        // Have to make a UsernamePasswordCredentials. The httpclient auth code
        // does an instanceof down in its guts.
        UsernamePasswordCredentials upc = null;
        try {
            upc = new UsernamePasswordCredentials(cred.getLogin(),
                    cred.getPassword());
            http.getState().setCredentials(new AuthScope(curi.getUURI().getHost(),
                    curi.getUURI().getPort(), cred.getRealm()), upc);
            logger.fine("Credentials for realm " + cred.getRealm() +
                    " for CrawlURI " + curi.toString() + " added to request: " +
                    result);

            http.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY,
                    Arrays.asList(AuthPolicy.DIGEST, AuthPolicy.BASIC));

            result = true;
        } catch (URIException e) {
            logger.severe("Failed to parse host from " + curi + ": " +
                    e.getMessage());
        }

        return result;
    }

}
