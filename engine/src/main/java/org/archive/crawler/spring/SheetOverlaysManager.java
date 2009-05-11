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
 
package org.archive.crawler.spring;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.collections.ListUtils;
import org.archive.crawler.datamodel.CrawlURI;
import org.archive.spring.OverlayMapsSource;
import org.archive.spring.Sheet;
import org.archive.util.PrefixFinder;
import org.archive.util.SurtPrefixSet;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Manager which marks-up CrawlURIs with the names of all applicable 
 * Sheets, and returns overlay maps by name. 
 * 
 * @contributor gojomo
 */
@SuppressWarnings("unchecked")
public class SheetOverlaysManager implements 
BeanFactoryAware, InitializingBean, OverlayMapsSource, ApplicationListener {

    BeanFactory beanFactory; 
    /** all Sheets applied by SURT-prefix */
    List<SheetForSurtPrefixes> surtSheets = ListUtils.EMPTY_LIST;
    /** all Sheets applied by DecideRule evaluation */ 
    List<SheetForDecideRuled> ruleSheets = ListUtils.EMPTY_LIST; 
    TreeMap<String,List<String>> sheetNamesBySurt = new TreeMap<String,List<String>>(); 
    
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    /**
     * Collect all rule-based Sheets. Typically autowired from the set
     * of all SheetForDecideRuled instances. 
     * @param ruleSheets
     */
    @SuppressWarnings("unchecked")
    @Autowired(required=false)
    public void setRuleSheets(List<SheetForDecideRuled> ruleSheets) {
        this.ruleSheets = ruleSheets;
        // always keep sorted by order
        Collections.sort(this.ruleSheets); 
    }

    /**
     * Collect all SURT-based Sheets. Typically autowired from the set
     * of all SheetForSurtPrefixes instances. 
     * @param surtSheets
     */
    @Autowired(required=false)
    public void setSurtSheets(List<SheetForSurtPrefixes> surtSheets) {
        this.surtSheets = surtSheets;
    }

    /** 
     * After all Sheets collected, build the mapping from SURT prefixes
     * to individual Sheet names.
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void afterPropertiesSet() throws Exception {
        for(SheetForSurtPrefixes s : surtSheets) {
            for(String prefix : s.getSurtPrefixes()) {
                List<String> sheetNames = sheetNamesBySurt.get(prefix);
                if(sheetNames == null) {
                    sheetNames = new LinkedList<String>();
                }
                sheetNames.add(s.getBeanName()); 
                sheetNamesBySurt.put(prefix, sheetNames); 
            }
        }
    }
    
    /**
     * Apply the proper overlays (by Sheet beanName) to the given CrawlURI. 
     * 
     * TODO: add guard against redundant application more than once? 
     * TODO: add mechanism for reapplying overlays after settings change? 
     * @param curi
     */
    public void applyOverlays(CrawlURI curi) {
        // apply SURT-based overlays
        String effectiveSurt = SurtPrefixSet.getCandidateSurt(curi.getUURI());
        @SuppressWarnings("unused")
        List<String> foundPrefixes = PrefixFinder.findKeys(sheetNamesBySurt, effectiveSurt);       
        for(String prefix : foundPrefixes) {
            for(String name : sheetNamesBySurt.get(prefix)) {
                curi.getOverlayNames().push(name);
            }
        }
        // apply deciderule-based overlays
        for(SheetForDecideRuled sheet : ruleSheets) {
            if(sheet.getRules().accepts(curi)) {
                curi.getOverlayNames().addFirst(sheet.getBeanName());
            }
        }
        // even if no overlays set, let creation of empty list signal
        // step has occurred -- helps ensure overlays added once-only
        curi.getOverlayNames();
    }

    /**
     * Retrieve the named overlay Map.
     * 
     * @see org.archive.spring.OverlayMapsSource#getOverlayMap(java.lang.String)
     */
    public Map<String, Object> getOverlayMap(String name) {
        Sheet sheet = (Sheet) beanFactory.getBean(name, Sheet.class);
        return sheet.getMap();
    }

    /** 
     * Ensure all sheets are 'primed' after the entire ApplicationContext
     * is assembled. This ensures target HasKeyedProperties beans know
     * any long paths by which their properties are addressed, and 
     * handles (by either PropertyEditor-conversion or a fast-failure)
     * any type-mismatches between overlay values and their target
     * properties.
     * @see org.springframework.context.ApplicationListener#onApplicationEvent(org.springframework.context.ApplicationEvent)
     */
    public void onApplicationEvent(ApplicationEvent event) {
        if(event instanceof ContextRefreshedEvent) {
            for(Sheet s: surtSheets) {
                s.prime(); 
            }
            for(Sheet s: ruleSheets) {
                s.prime(); 
            }
        }
    }
}