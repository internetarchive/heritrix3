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
package org.archive.io.warc;

import java.io.InputStream;
import java.net.URI;

import org.archive.io.warc.WARCConstants.WARCRecordType;
import org.archive.util.anvl.ANVLRecord;

public class WARCRecordInfo {

    protected WARCRecordType type;
    protected String url;
    protected String create14DigitDate;
    protected String mimetype;
    protected URI recordId; 
    protected ANVLRecord extraHeaders;
    protected InputStream contentStream;
    protected long contentLength;
    protected boolean enforceLength;

    public WARCRecordInfo(WARCRecordType type, String url) {
        this.type = type;
        this.url = url;
    }

    public String getCreate14DigitDate() {
        return create14DigitDate;
    }

    public void setCreate14DigitDate(String create14DigitDate) {
        this.create14DigitDate = create14DigitDate;
    }

    public String getMimetype() {
        return mimetype;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    public URI getRecordId() {
        return recordId;
    }

    public void setRecordId(URI recordId) {
        this.recordId = recordId;
    }

    public ANVLRecord getExtraHeaders() {
        return extraHeaders;
    }

    public void setExtraHeaders(ANVLRecord extraHeaders) {
        this.extraHeaders = extraHeaders;
    }

    public InputStream getContentStream() {
        return contentStream;
    }

    public void setContentStream(InputStream contentStream) {
        this.contentStream = contentStream;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public boolean isEnforceLength() {
        return enforceLength;
    }
    
    public boolean getEnforceLength() {
        return enforceLength;
    }
    
    public void setEnforceLength(boolean enforceLength) {
        this.enforceLength = enforceLength;
    }

    public WARCRecordType getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }
}
