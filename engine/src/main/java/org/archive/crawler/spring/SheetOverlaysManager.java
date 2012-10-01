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
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Logger;

import org.archive.modules.CrawlURI;
import org.archive.spring.OverlayMapsSource;
import org.archive.spring.Sheet;
import org.archive.util.PrefixFinder;
import org.archive.util.SurtPrefixSet;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeMismatchException;
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
public class SheetOverlaysManager implements 
BeanFactoryAware, OverlayMapsSource, ApplicationListener<ApplicationEvent> {
    private static final Logger logger = Logger.getLogger(SheetOverlaysManager.class.getName());
    

    protected BeanFactory beanFactory; 
    /** all SheetAssociations by DecideRule evaluation */ 
    protected SortedSet<DecideRuledSheetAssociation> ruleAssociations = 
        new ConcurrentSkipListSet<DecideRuledSheetAssociation>();
    protected NavigableMap<String,List<String>> sheetNamesBySurt = new ConcurrentSkipListMap<String,List<String>>(); 
    
    /** all sheets by (bean)name*/
    protected Map<String,Sheet> sheetsByName = new ConcurrentHashMap<String, Sheet>();
    
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
    public NavigableMap<String,List<String>> getSheetsNamesBySurt() {
        return this.sheetNamesBySurt;
    }
    /**
     * Collect all rule-based SheetAssociations. Typically autowired 
     * from the set of all DecideRuledSheetAssociation instances. 
     * @param ruleSheets
     */
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
     * Retrieve the named overlay Map.
     * 
     * @see org.archive.spring.OverlayMapsSource#getOverlayMap(java.lang.String)
     */
    public Map<String, Object> getOverlayMap(String name) {
        Sheet sheet = sheetsByName.get(name);
        if (sheet != null) {
            return sheet.getMap();
        } else {
            return null;
        }
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
    @Override
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
    
    //
    // Convenience methods for during-crawl overlay updates
    //
    
    /**
     * Add to named sheet an overlay of the given bean-path and new value. 
     * Creates the sheet if it does not already exist; re-primes the sheet
     * after the change to inform any targeted beans of new external paths. 
     * 
     * Only if/when the sheet is applied via associations will the overlay 
     * have a noticeably effect. Inserting/mutating/priming sheets should
     * only be done in a paused crawl. 
     * 
     * @param sheetName sheet name to change (or create)
     * @param beanPath target bean-path of overlay
     * @param value new value
     * @return old value, if any
     */
    public Object putSheetOverlay(String sheetName, String beanPath, Object value) {
        Sheet sheet = getOrCreateSheet(sheetName); 
        Object prevVal = sheet.getMap().put(beanPath, value);
        try {
            sheet.prime(); 
        } catch (TypeMismatchException tme) {
            // revert to presumably non-damaging value
            sheet.getMap().put(beanPath, prevVal);
            throw tme;
        }
        return prevVal; 
    }
    
    /**
     * Remove the given bean-path overlay in the named sheet. 
     * 
     * @param sheetName sheet name from which to remove overlay
     * @param beanPath overlay to remove
     * @return previous overlay value, if any
     */
    public Object removeSheetOverlay(String sheetName, String beanPath) {
        Sheet sheet = sheetsByName.get(sheetName); 
        if(sheet==null) {
            return null; 
        }
        // TODO: do all the externalPaths created by priming need eventual cleanup?
        return sheet.getMap().remove(beanPath);
    }
    
    /**
     * Delete a named sheet from all associations and the master named 
     * sheets map. 
     * @param sheetName sheet name to delete
     * @return true if any associations/sheet actually deleted
     */
    public boolean deleteSheet(String sheetName) {
        boolean anyDeleted = false; 
        // remove as target of any ruled-associations
        for(DecideRuledSheetAssociation assoc : ruleAssociations) {
            anyDeleted |= assoc.getTargetSheetNames().remove(sheetName);
        }
        // remove as target of any surt-associations
        for(List<String> sheetNames : sheetNamesBySurt.values()) {
            anyDeleted |= sheetNames.remove(sheetName);            
        }
        anyDeleted |= (null != sheetsByName.remove(sheetName)); 
        return anyDeleted;
    }
    
    /**
     * Get a Sheet of the given name, or create if it does not already 
     * exist. Provided for convenience of creating Sheet instances after 
     * the container has been built. 
     * 
     * To have effect as an overlay, the returned Sheet must be:
     * 
     * (1) filled with overlay entries, where the key is a full bean-path 
     * and the value the alternate overlay value; 
     * (2) primed via the prime() method, which will throw an exception
     * if the target bean-path does not address a compatible overlayable
     * value;
     * (3) associated to some URIs, by the addSurtAssociation or 
     * addRuledAssociation methods
     * 
     * @param name Sheet name to create; must be unique
     * @return created Sheet
     */
    public Sheet getOrCreateSheet(String name) {
        Sheet sheet = sheetsByName.get(name); 
        if(sheet==null) {
            sheet = new Sheet(); 
            sheet.setBeanFactory(beanFactory);
            sheet.setName(name); 
            sheet.setMap(new HashMap<String, Object>());
            sheetsByName.put(name, sheet);
        }
        return sheet;
    }
    
    /**
     * Apply the proper overlays (by Sheet beanName) to the given CrawlURI,
     * according to configured associations.  
     * 
     * TODO: add guard against redundant application more than once? 
     * TODO: add mechanism for reapplying overlays after settings change? 
     * @param curi
     */
    public void applyOverlaysTo(CrawlURI curi) {
        curi.setOverlayMapsSource(this); 
        // apply SURT-based overlays
        curi.getOverlayNames().clear(); // clear previous info
        String effectiveSurt = SurtPrefixSet.getCandidateSurt(curi.getPolicyBasisUURI());
        List<String> foundPrefixes = PrefixFinder.findKeys(sheetNamesBySurt, effectiveSurt);       
        for(String prefix : foundPrefixes) {
            for(String name : sheetNamesBySurt.get(prefix)) {
                curi.getOverlayNames().add(name);
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
}