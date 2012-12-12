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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;

/**
 * Unit test for {@link ExtractorJS}.
 *
 * @contributor gojomo
 * @contributor nlevitt
 */
public class ExtractorJSTest extends StringExtractorTestBase {

    final public static String[] VALID_TEST_DATA = new String[] {
        "var foo = \"http://www.example.com/outlink\";",
        "http://www.example.com/outlink",
        
        "var foo = \"<a href=\\\"http://www.example.com/outlink\\\">link in html in string</a>\";",
        "http://www.example.com/outlink",
        
        "var foo = \"<a href=\\\"http:\\/\\/www.example.com\\/outlink\\\">link in html in string with gratuitous escaping</a>\";",
        "http://www.example.com/outlink",

        "'string with spaces','http://example.com/outlink'",
        "http://example.com/outlink",
        
        "'string_with_\\'nested/quoted/relative/url.html\\'_inside_of_it'",
        "http://www.archive.org/foo/nested/quoted/relative/url.html",
        
        "scheme-less_\\\\\\'example.org/outlink\\\\\\'_with_extra_escaping on the quotes",
        "http://example.org/outlink",
        
        "var h1=Math.max.apply(null, $('.ro1 div.box').map(function(){return $(this).height()}).get());",
        null, // no outlinks
        
        "if(window.archive_analytics) { window.archive_analytics.values['server_ms'] = 114; window.archive_analytics.values['server_name'] = \"www26.us.archive.org\"};",
        "http://www26.us.archive.org/",
        
        "\"layoutAction\" : \"https:\\/\\/example.com\\/rest\\/dashboards\\/1.0\\/10000\\/layout\"",
        "https://example.com/rest/dashboards/1.0/10000/layout",
        
        "ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www",
        null, // expect absolute urls to have dots in the authority (sorry ipv6)
        
        "_gaq.push(['_setDomainName', '.example.com']);",
        null,
        
        "_gaq.push(['_setDomainName', 'example.com']);",
        "http://example.com/",
        
        "document.write(unescape(\"%3Cscript src='\" + cdJsHost + \"j.example.com/cr.js' type='text/javascript'%3E%3C/script%3E\"));",
        null,
        
        "scr.src = host + \"/j/roundtrip.js\";",
        "http://www.archive.org/j/roundtrip.js",

        "c+'\" width=\"'+b+'\" border=\"0\" src=\"'+a+'\" />'};return 0==a.example_conversion_format&&a.example_conversion_domain==f?'<a href=\"'+(r(b)+\"//services.example.com/sitestats/\"+({ar:1,bg:1,cs:1,da:1,de:1,el:1,en_AU:1,en_US:1,en_GB:1,es:1,",
        "http://services.example.com/sitestats/", // :-\
        
        "_CN:1,zh_TW:1}[a.example_conversion_language]?a.example_conversion_language+\".html\":\"en_US.html\")+\"?cid=\"+i(a.example_conversion_id))+",
        "http://www.archive.org/foo/en_US.html",
        
        "var blah='/question/mark/but/no/query/string.html?';",
        null,

        "var blah='/query/string/without/equals.html?foo';",
        null,

        "var blah='/query/string/without/value.html?foo=';",
        null,

        "var blah='/query/string/without/double/equals.html?foo==bar';",
        null,

        "var blah='/good/query/string.html?foo=bar';",
        "http://www.archive.org/good/query/string.html?foo=bar",
        
        "var blah='/good/query/string.html?foo=bar#with-good-hash-fragment';",
        "http://www.archive.org/good/query/string.html?foo=bar",
        
        "var blah='/good/query/string.html?foo=bar#with!bad!hash-fragment';",
        null,
        
        "var blah='/bad!/path/segment.html?foo=bar';",
        null,

        "var blah='/good/query/value/with/url-escaping.html?foo=bar%20bar';",
        "http://www.archive.org/good/query/value/with/url-escaping.html?foo=bar%20bar",
        
        "var blah='/good/query/value/with/url-escaping.html?foo=bar%20bar';",
        "http://www.archive.org/good/query/value/with/url-escaping.html?foo=bar%20bar",
    };
       
    @Override
    protected String[] getValidTestData() {
        return VALID_TEST_DATA;
    }
    
    @Override
    protected Extractor makeExtractor() {
        ExtractorJS result = new ExtractorJS();
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule();  
        result.setLoggerModule(ulm);
        return result;
    }
    
    @Override
    protected Collection<TestData> makeData(String content, String destURI)
    throws Exception {
        List<TestData> result = new ArrayList<TestData>();
        UURI src = UURIFactory.getInstance("http://www.archive.org/foo/dummy.js");
        CrawlURI euri = new CrawlURI(src, null, src, LinkContext.NAVLINK_MISC);
        Recorder recorder = createRecorder(content, "UTF-8");
        euri.setContentType("text/javascript");
        euri.setRecorder(recorder);
        euri.setContentSize(content.length());

        if (destURI != null) {
            UURI dest = UURIFactory.getInstance(destURI);
            Link link = new Link(src, dest, LinkContext.JS_MISC, Hop.SPECULATIVE);
            result.add(new TestData(euri, link));
        } else {
            result.add(new TestData(euri, null));
        }
        
        return result;
    }
}
