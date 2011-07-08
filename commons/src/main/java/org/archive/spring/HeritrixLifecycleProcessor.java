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

package org.archive.spring;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.context.support.DefaultLifecycleProcessor;

/**
 * Stand-in LifecycleProcessor to avoid a full automatic start() when our 
 * ApplicationContext (PathSharingContext) is built ('refreshed'). 
 * (That is, it restores the Spring 2.5 behavior.)
 * 
 * @contributor gojomo
 */
public class HeritrixLifecycleProcessor extends DefaultLifecycleProcessor implements BeanNameAware {
    String name; 
    
    @Override
    public void onRefresh() {
        // do nothing: we do not want to auto-start
    }

    @Override
    public void setBeanName(String name) {
        this.name = name; 
    }
    public String getBeanName() {
         return name; 
    }
}
