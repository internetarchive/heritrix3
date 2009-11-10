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
package org.archive.crawler.frontier.precedence;

import java.io.Serializable;

import org.archive.modules.CrawlURI;

/**
 * Superclass for URI precedence policies, which set a integer 
 * precedence value on individual URIs when they are first 
 * submitted to a frontier for scheduling. 
 * 
 * A URI's precedence directly affects where it lands in an 
 * individual URI queue, but does not affect a queue's precedence
 * relative to other queues *unless* a queue-precedencence-policy 
 * that consults URI precedence values is chosen. 
 * 
 */
abstract public class UriPrecedencePolicy implements Serializable {

    /**
     * Add a precedence value to the supplied CrawlURI, which is being 
     * scheduled onto a frontier queue for the first time. 
     * @param curi CrawlURI to assign a precedence value
     */
    abstract public void uriScheduled(CrawlURI curi);

}
