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

public class ExtractorYoutubeChannelFormatStreamTest extends ExtractorYoutubeFormatStreamTest {

    @Override
    protected String getTestUri() {
        return "https://www.youtube.com/user/BCCentralStudentLife";  	
    }

    @Override
    protected String getTestResourceFileName() {
        return "ExtractorYoutubeChannelFormatStream.txt";
    }

    @Override
    protected String[] getExpectedOutlinksAll() {
        return new String[] {
                "https://r17---sn-nwj7kne6.googlevideo.com/videoplayback?ip=208.70.27.190&key=yt5&mws=yes&fexp=927606%2C935705%2C941404%2C940908%2C936120%2C913434%2C939940%2C939944%2C936923%2C945044&itag=22&source=youtube&mv=m&sver=3&ratebypass=yes&uinitcwndbps=5992000&sparams=id%2Cip%2Cipbits%2Citag%2Cratebypass%2Crequiressl%2Csource%2Cuinitcwndbps%2Cupn%2Cexpire&requiressl=yes&ipbits=0&ms=au&expire=1400218541&mt=1400196716&id=o-ABd1wtC7lMy8Izs7zy3FECKHB29kJNKVWfwxMlLt2eqj&upn=4W79d7Gu5Jw&signature=9B7488918FC278EA29F659960BEE055AE821F903.38AA7828850805FC4F612A6A17E72ABF18C38FCA",
                "https://r17---sn-nwj7kne6.googlevideo.com/videoplayback?ip=208.70.27.190&key=yt5&mws=yes&fexp=927606%2C935705%2C941404%2C940908%2C936120%2C913434%2C939940%2C939944%2C936923%2C945044&itag=43&source=youtube&mv=m&sver=3&ratebypass=yes&uinitcwndbps=5992000&sparams=id%2Cip%2Cipbits%2Citag%2Cratebypass%2Crequiressl%2Csource%2Cuinitcwndbps%2Cupn%2Cexpire&requiressl=yes&ipbits=0&ms=au&expire=1400218541&mt=1400196716&id=o-ABd1wtC7lMy8Izs7zy3FECKHB29kJNKVWfwxMlLt2eqj&upn=4W79d7Gu5Jw&signature=6C00C9CCA86DFC1DCF67082BC696A0E48D7ADF08.776ACA7E0FAC4CC64E2AD1B00477D0F056F163",
                "https://r17---sn-nwj7kne6.googlevideo.com/videoplayback?ip=208.70.27.190&key=yt5&mws=yes&fexp=927606%2C935705%2C941404%2C940908%2C936120%2C913434%2C939940%2C939944%2C936923%2C945044&itag=18&source=youtube&mv=m&sver=3&ratebypass=yes&uinitcwndbps=5992000&sparams=id%2Cip%2Cipbits%2Citag%2Cratebypass%2Crequiressl%2Csource%2Cuinitcwndbps%2Cupn%2Cexpire&requiressl=yes&ipbits=0&ms=au&expire=1400218541&mt=1400196716&id=o-ABd1wtC7lMy8Izs7zy3FECKHB29kJNKVWfwxMlLt2eqj&upn=4W79d7Gu5Jw&signature=3AEF59C4FBF2A84C5BBEB310442D1C4138453FB3.2DEE85DB8A4BF31BCE92ADA41D59666845103CCB",
                "https://r17---sn-nwj7kne6.googlevideo.com/videoplayback?ip=208.70.27.190&key=yt5&mws=yes&fexp=927606%2C935705%2C941404%2C940908%2C936120%2C913434%2C939940%2C939944%2C936923%2C945044&itag=5&source=youtube&mv=m&sver=3&uinitcwndbps=5992000&ms=au&sparams=id%2Cip%2Cipbits%2Citag%2Crequiressl%2Csource%2Cuinitcwndbps%2Cupn%2Cexpire&requiressl=yes&ipbits=0&expire=1400218541&mt=1400196716&id=o-ABd1wtC7lMy8Izs7zy3FECKHB29kJNKVWfwxMlLt2eqj&upn=4W79d7Gu5Jw&signature=4266B17038E8DDB18CAAA72257E87393EB502038.B3FBA8B5CA4BFE829BB79CC09E29BEDA4F23B712",
                "https://r17---sn-nwj7kne6.googlevideo.com/videoplayback?ip=208.70.27.190&key=yt5&mws=yes&fexp=927606%2C935705%2C941404%2C940908%2C936120%2C913434%2C939940%2C939944%2C936923%2C945044&itag=36&source=youtube&mv=m&sver=3&uinitcwndbps=5992000&ms=au&sparams=id%2Cip%2Cipbits%2Citag%2Crequiressl%2Csource%2Cuinitcwndbps%2Cupn%2Cexpire&requiressl=yes&ipbits=0&expire=1400218541&mt=1400196716&id=o-ABd1wtC7lMy8Izs7zy3FECKHB29kJNKVWfwxMlLt2eqj&upn=4W79d7Gu5Jw&signature=458D350E6794E3E0D5227442A16E46E95E60DDFC.5D80AC2247DCA6C325358881BDD7E8F3B62D2BCA",
                "https://r17---sn-nwj7kne6.googlevideo.com/videoplayback?ip=208.70.27.190&key=yt5&mws=yes&fexp=927606%2C935705%2C941404%2C940908%2C936120%2C913434%2C939940%2C939944%2C936923%2C945044&itag=17&source=youtube&mv=m&sver=3&uinitcwndbps=5992000&ms=au&sparams=id%2Cip%2Cipbits%2Citag%2Crequiressl%2Csource%2Cuinitcwndbps%2Cupn%2Cexpire&requiressl=yes&ipbits=0&expire=1400218541&mt=1400196716&id=o-ABd1wtC7lMy8Izs7zy3FECKHB29kJNKVWfwxMlLt2eqj&upn=4W79d7Gu5Jw&signature=5301503384BBDFA72C02A6D6479CBFA8E06BDF85.326A10A482D488E4A58E704F6803E0AB99529F42" };
    }

    @Override
    protected String[] getExpectedOutlinksAllInItagPriority() {
        return new String[] {
                "https://r17---sn-nwj7kne6.googlevideo.com/videoplayback?ip=208.70.27.190&key=yt5&mws=yes&fexp=927606%2C935705%2C941404%2C940908%2C936120%2C913434%2C939940%2C939944%2C936923%2C945044&itag=43&source=youtube&mv=m&sver=3&ratebypass=yes&uinitcwndbps=5992000&sparams=id%2Cip%2Cipbits%2Citag%2Cratebypass%2Crequiressl%2Csource%2Cuinitcwndbps%2Cupn%2Cexpire&requiressl=yes&ipbits=0&ms=au&expire=1400218541&mt=1400196716&id=o-ABd1wtC7lMy8Izs7zy3FECKHB29kJNKVWfwxMlLt2eqj&upn=4W79d7Gu5Jw&signature=6C00C9CCA86DFC1DCF67082BC696A0E48D7ADF08.776ACA7E0FAC4CC64E2AD1B00477D0F056F163",
                "https://r17---sn-nwj7kne6.googlevideo.com/videoplayback?ip=208.70.27.190&key=yt5&mws=yes&fexp=927606%2C935705%2C941404%2C940908%2C936120%2C913434%2C939940%2C939944%2C936923%2C945044&itag=18&source=youtube&mv=m&sver=3&ratebypass=yes&uinitcwndbps=5992000&sparams=id%2Cip%2Cipbits%2Citag%2Cratebypass%2Crequiressl%2Csource%2Cuinitcwndbps%2Cupn%2Cexpire&requiressl=yes&ipbits=0&ms=au&expire=1400218541&mt=1400196716&id=o-ABd1wtC7lMy8Izs7zy3FECKHB29kJNKVWfwxMlLt2eqj&upn=4W79d7Gu5Jw&signature=3AEF59C4FBF2A84C5BBEB310442D1C4138453FB3.2DEE85DB8A4BF31BCE92ADA41D59666845103CCB",
                "https://r17---sn-nwj7kne6.googlevideo.com/videoplayback?ip=208.70.27.190&key=yt5&mws=yes&fexp=927606%2C935705%2C941404%2C940908%2C936120%2C913434%2C939940%2C939944%2C936923%2C945044&itag=5&source=youtube&mv=m&sver=3&uinitcwndbps=5992000&ms=au&sparams=id%2Cip%2Cipbits%2Citag%2Crequiressl%2Csource%2Cuinitcwndbps%2Cupn%2Cexpire&requiressl=yes&ipbits=0&expire=1400218541&mt=1400196716&id=o-ABd1wtC7lMy8Izs7zy3FECKHB29kJNKVWfwxMlLt2eqj&upn=4W79d7Gu5Jw&signature=4266B17038E8DDB18CAAA72257E87393EB502038.B3FBA8B5CA4BFE829BB79CC09E29BEDA4F23B712",
                "https://r17---sn-nwj7kne6.googlevideo.com/videoplayback?ip=208.70.27.190&key=yt5&mws=yes&fexp=927606%2C935705%2C941404%2C940908%2C936120%2C913434%2C939940%2C939944%2C936923%2C945044&itag=36&source=youtube&mv=m&sver=3&uinitcwndbps=5992000&ms=au&sparams=id%2Cip%2Cipbits%2Citag%2Crequiressl%2Csource%2Cuinitcwndbps%2Cupn%2Cexpire&requiressl=yes&ipbits=0&expire=1400218541&mt=1400196716&id=o-ABd1wtC7lMy8Izs7zy3FECKHB29kJNKVWfwxMlLt2eqj&upn=4W79d7Gu5Jw&signature=458D350E6794E3E0D5227442A16E46E95E60DDFC.5D80AC2247DCA6C325358881BDD7E8F3B62D2BCA",
                "https://r17---sn-nwj7kne6.googlevideo.com/videoplayback?ip=208.70.27.190&key=yt5&mws=yes&fexp=927606%2C935705%2C941404%2C940908%2C936120%2C913434%2C939940%2C939944%2C936923%2C945044&itag=17&source=youtube&mv=m&sver=3&uinitcwndbps=5992000&ms=au&sparams=id%2Cip%2Cipbits%2Citag%2Crequiressl%2Csource%2Cuinitcwndbps%2Cupn%2Cexpire&requiressl=yes&ipbits=0&expire=1400218541&mt=1400196716&id=o-ABd1wtC7lMy8Izs7zy3FECKHB29kJNKVWfwxMlLt2eqj&upn=4W79d7Gu5Jw&signature=5301503384BBDFA72C02A6D6479CBFA8E06BDF85.326A10A482D488E4A58E704F6803E0AB99529F42" };
    }
    
    @Override
    protected int getExpectedOutlinkCountOnlyInItagPriorityBigLimit() {
        return 1;
    }
    
    @Override
    protected String[] getExpectedSingleDefaultOutlink() {
        return new String[] { "https://r17---sn-nwj7kne6.googlevideo.com/videoplayback?ip=208.70.27.190&key=yt5&mws=yes&fexp=927606%2C935705%2C941404%2C940908%2C936120%2C913434%2C939940%2C939944%2C936923%2C945044&itag=22&source=youtube&mv=m&sver=3&ratebypass=yes&uinitcwndbps=5992000&sparams=id%2Cip%2Cipbits%2Citag%2Cratebypass%2Crequiressl%2Csource%2Cuinitcwndbps%2Cupn%2Cexpire&requiressl=yes&ipbits=0&ms=au&expire=1400218541&mt=1400196716&id=o-ABd1wtC7lMy8Izs7zy3FECKHB29kJNKVWfwxMlLt2eqj&upn=4W79d7Gu5Jw&signature=9B7488918FC278EA29F659960BEE055AE821F903.38AA7828850805FC4F612A6A17E72ABF18C38FCA" };
    }
    
    @Override
    protected Extractor makeExtractor() {
        ExtractorYoutubeChannelFormatStream e = new ExtractorYoutubeChannelFormatStream();
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule();
        e.setLoggerModule(ulm);
        return e;
    }
    
    @Override
    public void testAlternatePage() throws Exception {
        // do nothing
    }

    @Override
    public void testPriority() throws Exception {
        // XXX do nothing -- could implement this test
    }
}
