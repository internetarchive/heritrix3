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

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.commons.httpclient.URIException;
import org.apache.http.HttpRequest;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.util.EntityUtils;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.ProcessorTestBase;
import org.archive.modules.fetcher.BasicExecutionAwareEntityEnclosingRequest;
import org.archive.modules.fetcher.FetchHTTP;
import org.archive.modules.fetcher.FetchHTTPRequest;
import org.archive.modules.fetcher.FetchHTTPTests;
import org.archive.modules.forms.HTMLForm.NameValue;

public class FormLoginProcessorTest extends ProcessorTestBase {

    static class FetchHTTPRequestSpy extends FetchHTTPRequest {
        public FetchHTTPRequestSpy(FetchHTTP fetcher, CrawlURI curi) throws URIException {
            super(fetcher, curi);
        }
        public HttpRequest getRequest() {
            return request;
        }
    }

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
        form.addField("hidden", "crazy🐒monkey", "úhóh");
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

        assertEquals(1, curi.getOutLinks().size());
        CrawlURI submitCuri = curi.getOutLinks().toArray(new CrawlURI[0])[0];
        assertEquals("http://example.com/login", submitCuri.toString());
        assertEquals(FetchType.HTTP_POST, submitCuri.getFetchType());

        FetchHTTPRequestSpy reqSpy = new FetchHTTPRequestSpy(
                FetchHTTPTests.newTestFetchHttp(getClass().getName()),
                submitCuri);
        assertTrue(reqSpy.getRequest() instanceof BasicExecutionAwareEntityEnclosingRequest);
        BasicExecutionAwareEntityEnclosingRequest req = (BasicExecutionAwareEntityEnclosingRequest) reqSpy.getRequest();
        assertTrue(req.getEntity() instanceof UrlEncodedFormEntity);
        assertTrue(req.toString().startsWith("POST /login"));
        assertEquals("username-form-field=jdoe&password-form-field=********&crazy%F0%9F%90%92monkey=%C3%BAh%C3%B3h",
                EntityUtils.toString(req.getEntity()));
    }

    public void testMultipartFormLogin() throws Exception {
        CrawlURI curi = makeCrawlURI("http://example.com/");

        HTMLForm form = new HTMLForm();
        form.addField("text", "username-form-field", "");
        form.addField("password", "password-form-field", "");
        form.addField("hidden", "crazy🐒monkey", "úhóh");
        form.setMethod("post");
        form.setAction("/login");
        form.setEnctype("multipart/form-data");
        curi.getDataList(ExtractorHTMLForms.A_HTML_FORM_OBJECTS).add(form);

        FormLoginProcessor p = (FormLoginProcessor) makeModule();
        p.setLoginUsername("jdoe");
        p.setLoginPassword("********");
        p.setApplicableSurtPrefix("http://(com,example,)");

        p.process(curi);
        assertEquals(1, curi.getDataList(A_WARC_RESPONSE_HEADERS).size()); 
        assertEquals("WARC-Simple-Form-Province-Status: 0,0,http://(com,example,)", curi.getDataList(A_WARC_RESPONSE_HEADERS).get(0));
        assertTrue(curi.getAnnotations().contains("submit:/login"));

        assertEquals(1, curi.getOutLinks().size());
        CrawlURI submitCuri = curi.getOutLinks().toArray(new CrawlURI[0])[0];
        assertEquals("http://example.com/login", submitCuri.toString());
        assertEquals(FetchType.HTTP_POST, submitCuri.getFetchType());

        FetchHTTPRequestSpy reqSpy = new FetchHTTPRequestSpy(
                FetchHTTPTests.newTestFetchHttp(getClass().getName()),
                submitCuri);
        assertTrue(reqSpy.getRequest() instanceof BasicExecutionAwareEntityEnclosingRequest);
        BasicExecutionAwareEntityEnclosingRequest req = (BasicExecutionAwareEntityEnclosingRequest) reqSpy.getRequest();
        assertEquals("org.apache.http.entity.mime.MultipartFormEntity", req.getEntity().getClass().getName());
        assertTrue(req.toString().startsWith("POST /login"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        req.getEntity().writeTo(baos);
        // --e5XWkWxQ2EXBQAEPQ7n3yyvv9bI-8YIHok\r\nContent-Disposition: form-data; name=\"username-form-field\"\r\n\r\njdoe\r\n--e5XWkWxQ2EXBQAEPQ7n3yyvv9bI-8YIHok\r\nContent-Disposition: form-data; name=\"password-form-field\"\r\n\r\n********\r\n--e5XWkWxQ2EXBQAEPQ7n3yyvv9bI-8YIHok\r\nContent-Disposition: form-data; name=\"crazy&#128018;monkey\"\r\n\r\n&#250;h&#243;h\r\n--e5XWkWxQ2EXBQAEPQ7n3yyvv9bI-8YIHok--\r\n
        assertTrue(baos.toString("ascii").matches("--([a-zA-Z0-9_-]{30,41})\r\nContent-Disposition: form-data; name=\"username-form-field\"\r\n\r\njdoe\r\n--\\1\r\nContent-Disposition: form-data; name=\"password-form-field\"\r\n\r\n\\*\\*\\*\\*\\*\\*\\*\\*\r\n--\\1\r\nContent-Disposition: form-data; name=\"crazy&#128018;monkey\"\r\n\r\n&#250;h&#243;h\r\n--\\1--\r\n"));
    }

    public void testEscapeForMultipart() {
        assertEquals("abcd", FetchHTTPRequest.escapeForMultipart("abcd"));
        assertEquals("abcd&#233;", FetchHTTPRequest.escapeForMultipart("abcdé"));
        assertEquals("abcd&#233;&#128556;", FetchHTTPRequest.escapeForMultipart("abcdé😬"));
    }

    public void testFormLoginExtraInputs() throws Exception {
        CrawlURI curi = makeCrawlURI("http://example.com/");

        HTMLForm form = new HTMLForm();
        form.addField("text", "username-form-field", "");
        form.addField("password", "password-form-field", "");
        form.addField("text", "some-other-form-field", "default value!");
        form.addField("hidden", "hidden-field", "hidden value!");
        form.addField("checkbox", "checkbox-field", "unchecked-value", false);
        form.addField("checkbox", "checkbox-field", "checked-value", true);
        form.addField("radio", "radio-field", "unchecked-value", false);
        form.addField("checkbox", "radio-field", "checked-value", true);
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

        assertEquals(1, curi.getOutLinks().size());
        CrawlURI submitCuri = curi.getOutLinks().toArray(new CrawlURI[0])[0];
        assertEquals("http://example.com/login", submitCuri.toString());
        assertEquals(FetchType.HTTP_POST, submitCuri.getFetchType());
        @SuppressWarnings("unchecked")
        List<NameValue> submitData = (List<NameValue>) submitCuri.getData().get(CoreAttributeConstants.A_SUBMIT_DATA);
        assertEquals(6, submitData.size());
        assertEquals("username-form-field", submitData.get(0).name);
        assertEquals("jdoe", submitData.get(0).value);
        assertEquals("password-form-field", submitData.get(1).name);
        assertEquals("********", submitData.get(1).value);
        assertEquals("some-other-form-field", submitData.get(2).name);
        assertEquals("default value!", submitData.get(2).value);
        assertEquals("hidden-field", submitData.get(3).name);
        assertEquals("hidden value!", submitData.get(3).value);
        assertEquals("checkbox-field", submitData.get(4).name);
        assertEquals("checked-value", submitData.get(4).value);
        assertEquals("radio-field", submitData.get(5).name);
        assertEquals("checked-value", submitData.get(5).value);
    }
}
