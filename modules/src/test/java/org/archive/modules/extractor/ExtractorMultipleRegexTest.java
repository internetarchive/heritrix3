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
import java.util.LinkedHashMap;
import java.util.List;

import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;

public class ExtractorMultipleRegexTest extends StringExtractorTestBase {
    final public static String[] VALID_TEST_DATA = new String[] {
        // https://www.facebook.com/NorthCarolinaStateParks some time in the past
        "{\"profile_id\":143412869029,\"start\":1351753200,\"end" +
        "\":1354348799,\"query_type\":31,\"section_pagelet_id\":\"" +
        "pagelet_timeline_earlier_this_month_all\",\"load_immediately\"" +
        ":false},false,null,1,-1]],[\"TimelineContentLoader\",\"load" +
        "SectionOnClick\",[\"m959614_181\"],[{\"__m\":\"m959614_181\"}" +
        ",\"month_2012_10\"]],[\"TimelineContentLoader\",\"registerTim" +
        "ePeriod\",[\"m959614_182\"],[{\"__m\":\"m959614_182\"},\"month_20" +
        "12_10\",{\"profile_id\":143412869029,\"start\":1349074800,\"end\":13517" +
        "53199,\"query_type\":25,\"section_pagelet_id\":\"pagelet_timeline_month_al" +
        "l_last\",\"load_immediately\":false},false,null,2,-1]],[\"TimelineContent" +
        "Loader\",\"loadSectionOnClick\",[\"m959614_183\"],[{\"__m\":\"m959614" +
        "_183\"},\"year_2012\"]],[\"TimelineContentLoader\",\"registerTimePeri" +
        "od\",[\"m959614_184\"],[{\"__m\":\"m959614_184\"},\"year_2012\",{\"profile_" +
        "id\":143412869029,\"start\":1325404800,\"end\":1357027199,\"query_type\":8" +
        ",\"filter_after_timestamp\":1349074799,\"section_pagelet_id\":\"pagelet_" +
        "timeline_year_current\",\"load_immediately\":false},false,null,3,\"101.67" +
        "277588916\"]],\n [\"TimelineContentLoader\",\"setExpandLoadDataForSection\",[],[\"y" +
        "ear_2012\",{\"profile_id\":143412869029,\"start\":1325404800,\"end\":135" +
        "7027199,\"query_type\":9}]],[\"TimelineContentLoader\",\"loadSectionOnClick" +
        "\",[\"m959614_185\"],[{\"__m\":\"m959614_185\"},\"year_2011\"]],[\"TimelineCo" +
        "ntentLoader\",\"registerTimePeriod\",[\"m959614_186\"],[{\"__m\":\"m959614_186\"}" +
        ",\"year_2011\",{\"profile_id\":143412869029,\"start\":1293868800,\"end\":13254" +
        "04799,\"query_type\":8,\"section_pagelet_id\":\"pagelet_timeline_year_last\",\"l" +
        "oad_immediately\":false},false,null,4,\"86.97249252724\"]],[\"TimelineContentLoad" +
        "er\",\"setExpandLoadDataForSection\",[],[\"year_2011\",{\"profile_id\":143412869029" +
        ",\"start\":1293868800,\"end\":1325404799,\"query_type\":9}]],[\"TimelineContentLoad" +
        "er\",\"loadSectionOnClick\",[\"m959614_187\"],[{\"__m\":\"m959614_187\"},\"year_20" +
        "10\"]],[\"TimelineContentLoader\",\"registerTimePeriod\",[\"m959614_188\"],[{\"_" +
        "_m\":\"m959614_188\"},\"year_2010\",{\"profile_id\":143412869029,\"start\":126233" +
        "2800,\"end\":1293868799,\"query_type\":8,\"section_pagelet_id\":\"pagelet\n _timel" +
        "ine_year_2010\",\"load_immediately\":false},false,null,5,\"88.440195233432\"]],[\"Ti" +
        "melineContentLoader\",\"setExpandLoadDataForSection\",[],[\"year_2010\",{\"profile_i" +
        "d\":143412869029,\"start\":1262332800,\"end\":1293868799,\"query_type\":9}]],[\"Time" +
        "lineContentLoader\",\"loadSectionOnClick\",[\"m959614_189\"],[{\"__m\":\"m959614_189" +
        "\"},\"year_2009\"]],[\"TimelineContentLoader\",\"registerTimePeriod\",[\"m959614_190" +
        "\"],[{\"__m\":\"m959614_190\"},\"year_2009\",{\"profile_id\":143412869029,\"start\":" +
        "1230796800,\"end\":1262332799,\"query_type\":8,\"section_pagelet_id\":\"pagelet_time" +
        "line_year_2009\",\"load_immediately\":false},false,null,6,\"41.800676041109\"]],[\"T" +
        "imelineContentLoader\",\"setExpandLoadDataForSection\",[],[\"year_2009\",{\"profile_" +
        "id\":143412869029,\"start\":1230796800,\"end\":1262332799,\"query_type\":9}]]]},\"cs" +
        "s\":[\"M501h\",\"o8oNt\"],\"js\":[\"dERRF\",\"yEdv1\",\"JWWMg\"],\"id\":\"timeline_s" +
        "ection_placeholders\",\"phase\":3})</script>\n",
        "http://nourl.com/dne",
    };
    
    @Override
    protected String[] getValidTestData() {
        return VALID_TEST_DATA;
    }

    /*
     * Settings for extracting scroll-down ajax urls from facebook, as they are
     * constructed as of Nov 14 2012.
     * 
     * <bean class="org.archive.modules.extractor.ExtractorMultipleRegex">
     *  <property name="uriRegex" value="^https?://(?:www\.)?facebook\.com/[^/?]+$" />
     *  <property name="contentRegexes">
     *   <map>
     *    <entry key="jsonBlob" value='\{("profile_id":\d+,[^}]+)\}' />
     *    <entry key="ajaxpipeToken" value='"ajaxpipe_token":"([^"]+)"' />
     *    <entry key="timeCutoff" value='"setTimeCutoff",[^,]*,\[(\d+)\]\]' />
     *   </map>
     *  </property>
     *  <property name="template">
     *   <value>/ajax/pagelet/generic.php/ProfileTimelineSectionPagelet?ajaxpipe=1&amp;ajaxpipe_token=${ajaxpipeToken[1]}&amp;no_script_path=1&amp;data=${java.net.URLEncoder.encode('{' + jsonBlob[1] + ',"time_cutoff":' + timeCutoff[1] + ',"force_no_friend_activity":false}', 'UTF-8')}&amp;__user=0&amp;__a=1&amp;__adt=${jsonBlobIndex+1}</value>
     *   </property>
     * </bean>
     */
    @Override
    protected Extractor makeExtractor() {
        ExtractorMultipleRegex extractor = new ExtractorMultipleRegex();
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule();  
        extractor.setLoggerModule(ulm);
        
        extractor.setUriRegex("^https?://(?:www\\.)?facebook\\.com/[^/?]+$");
        
        LinkedHashMap<String, String> contentRegexes = new LinkedHashMap<String,String>();
        contentRegexes.put("jsonBlob", "\\{(\"profile_id\":\\d+,[^}]+)\\}");
        contentRegexes.put("ajaxpipeToken", "\"ajaxpipe_token\":\"([^\"]+)\"");
        contentRegexes.put("timeCutoff", "\"setTimeCutoff\",[^,]*,\\[(\\d+)\\]\\]");
        extractor.setContentRegexes(contentRegexes);
        
        extractor.setTemplate("/ajax/pagelet/generic.php/ProfileTimelineSectionPagelet"
                        + "?ajaxpipe=1&ajaxpipe_token=${ajaxpipeToken[1]}&no_script_path=1"
                        + "&data=${java.net.URLEncoder.encode('{' + jsonBlob[1] + ',\"time_cutoff\":' + timeCutoff[1] + ',\"force_no_friend_activity\":false}', 'UTF-8')}"
                        + "&__user=0&__a=1&__adt=${jsonBlobIndex+1}");

        return extractor;
    }

    @Override
    protected Collection<TestData> makeData(String content, String destURI)
            throws Exception {
        List<TestData> result = new ArrayList<TestData>();
        UURI src = UURIFactory.getInstance("https://www.facebook.com/NorthCarolinaStateParks");
        CrawlURI euri = new CrawlURI(src, null, null, 
                LinkContext.NAVLINK_MISC);
        Recorder recorder = createRecorder(content);
        euri.setContentType("text/html");
        euri.setRecorder(recorder);
        euri.setContentSize(content.length());
                
        UURI dest = UURIFactory.getInstance(destURI);
        Link link = new Link(src, dest, HTMLLinkContext.INFERRED_MISC, Hop.INFERRED);
        result.add(new TestData(euri, link));
        
        euri = new CrawlURI(src, null, null, LinkContext.NAVLINK_MISC);
        recorder = createRecorder(content);
        euri.setContentType("application/xhtml");
        euri.setRecorder(recorder);
        euri.setContentSize(content.length());
        result.add(new TestData(euri, link));
        
        return result;
    }

}
