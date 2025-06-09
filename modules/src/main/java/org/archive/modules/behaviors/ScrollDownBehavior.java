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

package org.archive.modules.behaviors;

/**
 * Scrolls the page down until it reaches the bottom (or until a timeout is reached).
 */
public class ScrollDownBehavior implements Behavior {
    private long timeout = 5000;
    private int scrollInterval = 50;

    /**
     * Maximum time to wait to reach the bottom of the page, in milliseconds.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * How many milliseconds to wait between each scroll step.
     */
    public void setScrollInterval(int scrollInterval) {
        this.scrollInterval = scrollInterval;
    }

    @Override
    public void run(Page page) {
        page.evalPromise(/* language=JavaScript */ """
                (timeout, scrollInterval) => {
                return new Promise((doneCallback, reject) => {
                    const startTime = Date.now();
                
                    function scroll() {
                        if (window.innerHeight + window.scrollY >= document.body.offsetHeight) {
                            // We've reached the bottom of the page
                            doneCallback();
                            return;
                        }
                
                        if (Date.now() - startTime > timeout) {
                            // We've exceeded the timeout
                            doneCallback();
                            return;
                        }
                
                        const scrollStep = document.scrollingElement.clientHeight * 0.2;
                
                        window.scrollBy({top: scrollStep, behavior: "instant"});
                        setTimeout(scroll, scrollInterval);
                    }
                
                    scroll();
                })}""", timeout, scrollInterval);
    }
}
