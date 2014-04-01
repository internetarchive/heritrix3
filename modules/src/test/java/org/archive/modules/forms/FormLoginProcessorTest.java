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

package org.archive.modules.forms;

import static org.archive.modules.CoreAttributeConstants.A_WARC_RESPONSE_HEADERS;

import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.util.EncodingUtil;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessorTestBase;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.forms.FormLoginProcessor;

public class FormLoginProcessorTest extends ProcessorTestBase {

    public void testNoFormLogin() throws Exception {
        CrawlURI curi = makeCrawlURI("http://example.com/");

        FormLoginProcessor p = (FormLoginProcessor) makeModule();
        p.setLoginUsername("jdoe");
        p.setLoginPassword("********");
        p.setApplicableSurtPrefix("http://(com,example,)");

        p.process(curi);

        assertEquals(1, curi.getDataList(A_WARC_RESPONSE_HEADERS).size()); 
        assertEquals("WARC-Simple-Form-Province-Status: 0,0,http://(com,example,)", curi.getDataList(A_WARC_RESPONSE_HEADERS).get(0)); 
    }

    public void testFormLogin() throws Exception {
        CrawlURI curi = makeCrawlURI("http://example.com/");

        HTMLForm form = new HTMLForm();
        form.addField("text", "username-form-field", "");
        form.addField("password", "password-form-field", "");
        form.setMethod("post");
        form.setAction("/login");
        curi.getDataList(ExtractorHTMLForms.A_HTML_FORM_OBJECTS).add(form);

        FormLoginProcessor p = (FormLoginProcessor) makeModule();
        p.setLoginUsername("jdoe");
        p.setLoginPassword("********");
        p.setApplicableSurtPrefix("http://(com,example,)");

        p.process(curi);
        assertEquals(1, curi.getDataList(A_WARC_RESPONSE_HEADERS).size()); 
        assertEquals("WARC-Simple-Form-Province-Status: 0,0,http://(com,example,)", curi.getDataList(A_WARC_RESPONSE_HEADERS).get(0));
        assertTrue(curi.getAnnotations().contains("submit:/login"));

        assertEquals(1, curi.getOutCandidates().size());
        CrawlURI submitCuri = curi.getOutCandidates().toArray(new CrawlURI[0])[0];
        assertEquals("http://example.com/login", submitCuri.toString());
        assertEquals(FetchType.HTTP_POST, submitCuri.getFetchType());
        NameValuePair[] queryParams = (NameValuePair[]) submitCuri.getData().get(CoreAttributeConstants.A_SUBMIT_DATA);
        String queryString = EncodingUtil.formUrlEncode(queryParams, "UTF-8");
        assertEquals("username-form-field=jdoe&password-form-field=********", queryString);
    }
}
