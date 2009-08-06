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
package org.archive.crawler.event;

import org.archive.modules.CrawlURI;
import org.springframework.context.ApplicationEvent;

public class CrawlURIDispositionEvent extends ApplicationEvent {
    public enum Disposition {
        SUCCEEDED, FAILED, DISREGARDED, DEFERRED_FOR_RETRY
    }
    
    private static final long serialVersionUID = 1L;
    protected CrawlURI curi;
    protected Disposition disposition;

    public CrawlURIDispositionEvent(Object source, CrawlURI curi, Disposition disposition) {
        super(source);
        this.curi = curi;
        this.disposition = disposition; 
    }

    public Disposition getDisposition() {
        return this.disposition;
    }

    public CrawlURI getCrawlURI() {
        return this.curi;
    }
    
}
