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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.archive.modules.CrawlURI;
import org.archive.spring.OverlayMapsSource;
import org.archive.spring.Sheet;
import org.archive.util.PrefixFinder;
import org.archive.util.SurtPrefixSet;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
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
BeanFactoryAware, OverlayMapsSource, ApplicationListener {
    private static final Logger logger = Logger.getLogger(SheetOverlaysManager.class.getName());
    

    BeanFactory beanFactory; 
    /** all SheetAssociations by DecideRule evaluation */ 
    SortedSet<DecideRuledSheetAssociation> ruleAssociations = 
        new TreeSet<DecideRuledSheetAssociation>();
    TreeMap<String,List<String>> sheetNamesBySurt = new TreeMap<String,List<String>>(); 
    
    /** all sheets by (bean)name*/
    Map<String,Sheet> sheetsByName = new HashMap<String, Sheet>();
    
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
    
    /**
     * Collect all Sheets, by beanName. 
     * @param map
     */
    @Autowired(required=false)
    public void setSheetsByName(Map<String,Sheet> map) {
        this.sheetsByName = map;
    }
    /**
     * Sheets, by name; starts with all autowired Sheets but others
     * may be added by other means (mid-crawl reconfiguration). 
     * @return map of Sheets by their String name
     */
    public Map<String,Sheet> getSheetsByName() {
        return this.sheetsByName;
    }

    /**
     * All DecideRuledSheetAssociations, in Ordered order
     *  
     * @return set of DecideRuledSheetAssociation
     */
    public SortedSet<DecideRuledSheetAssociation> getRuleAssociations() {
        return this.ruleAssociations;
    }
    
    /**
     * Sheet names, by the SURT prefix to which they should be applied.
     * 
     * @return map of Sheet names by their configured SURT
     */
    public TreeMap<String,List<String>> getSheetsNamesBySurt() {
        return this.sheetNamesBySurt;
    }
    /**
     * Collect all rule-based SheetAssociations. Typically autowired 
     * from the set of all DecideRuledSheetAssociation instances. 
     * @param ruleSheets
     */
    @SuppressWarnings("unchecked")
    @Autowired(required=false)
    public void addRuleAssociations(Set<DecideRuledSheetAssociation> associations) {
        // always keep sorted by order
        this.ruleAssociations.clear();
        this.ruleAssociations.addAll(associations);
    }
    
    public void addRuleAssociation(DecideRuledSheetAssociation assoc) {
        this.ruleAssociations.add(assoc); 
    }

    /**
     * Collect all SURT-based SheetAssociations. Typically autowired 
     * from the set of all SurtPrefixesSheetAssociation instances
     * declared in the initial configuration. 
     * @param surtSheets
     */
    @Autowired(required=false)
    public void addSurtAssociations(List<SurtPrefixesSheetAssociation> associations) {
        for(SurtPrefixesSheetAssociation association : associations) {
            addSurtsAssociation(association);
        }
    }
    
    public void addSurtAssociation(String prefix, String sheetName) {
        List<String> sheetNames = sheetNamesBySurt.get(prefix);
        if(sheetNames == null) {
            sheetNames = new LinkedList<String>();
        }
        sheetNames.add(sheetName); 
        sheetNamesBySurt.put(prefix, sheetNames); 
    }
    
    public boolean removeSurtAssociation(String prefix, String sheetName) {
        List<String> sheetNames = sheetNamesBySurt.get(prefix);
        if(sheetNames == null) {
            // no such association
            return false; 
        }
        return sheetNames.remove(sheetName); 
    }

    /** 
     * Add an individual surtsAssociation to the sheetNamesBySurt map.
     */
    public void addSurtsAssociation(SurtPrefixesSheetAssociation assoc) {
        for(String prefix : assoc.getSurtPrefixes()) {
            for(String s : assoc.getTargetSheetNames()) {
                addSurtAssociation(prefix, s);
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
        for(DecideRuledSheetAssociation assoc : ruleAssociations) {
            if(assoc.getRules().accepts(curi)) {
                curi.getOverlayNames().addAll(assoc.getTargetSheetNames());
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
        return sheetsByName.get(name).getMap();
    }

    /** 
     * Ensure all sheets are 'primed' after the entire ApplicatiotnContext
     * is assembled. This ensures target HasKeyedProperties beans know
     * any long paths by which their properties are addressed, and 
     * handles (by either PropertyEditor-conversion or a fast-failure)
     * any type-mismatches between overlay values and their target
     * properties.
     * @see org.springframework.context.ApplicationListener#onApplicationEvent(org.springframework.context.ApplicationEvent)
     */
    public void onApplicationEvent(ApplicationEvent event) {
        if(event instanceof ContextRefreshedEvent) {
            for(Sheet s: sheetsByName.values()) {
                s.prime(); // exception if Sheet can't target overridable properties
            }
            // log warning for any sheets named but not present
            HashSet<String> allSheetNames = new HashSet<String>();
            for(DecideRuledSheetAssociation assoc : ruleAssociations) {
                allSheetNames.addAll(assoc.getTargetSheetNames());
            }
            for(List<String> names : sheetNamesBySurt.values()) {
                allSheetNames.addAll(names);
            }
            for(String name : allSheetNames) {
                if(!sheetsByName.containsKey(name)) {
                    logger.warning("sheet '"+name+"' referenced but absent");
                }
            }
        }
    }
    
    /**
     * Create a new Sheet of the given name. Provided for convenience of
     * creating Sheet instances after the container has been built. 
     * 
     * To have effect as an overlay, the returned Sheet must be:
     * 
     * (1) filled with overlay entries, where the key is a full bean-path 
     * and the value the alternate overlay value; 
     * (2) primed via the prime() method, which will throw an exception
     * if the target bean-path does not address a compatible overlayable
     * value;
     * (3) added back to the SheetOverlayManager's sheetsByName map;
     * (4) associated to some URIs, by the addSurtAssociation or 
     * addRuledAssociation methods
     * 
     * @param name Sheet name to create; must be unique
     * @return created Sheet
     */
    public Sheet createSheet(String name) {
        if(sheetsByName.containsKey(name)) {
            throw new IllegalArgumentException("sheet '"+name+"' already exists"); 
        }
        Sheet sheet = new Sheet(); 
        sheet.setBeanFactory(beanFactory);
        sheet.setName(name); 
        return sheet; 
    }
}