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
import java.beans.PropertyChangeEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Required;


/**
 * Collection of overrides: alternative values for object properties
 * that should apply in some contexts. The target is specified as an
 * arbitrarily-long property-path, a string describing how to access 
 * the property starting from a beanName in a BeanFactory. 
 * 
 * Once a Sheet has all its mappings, and all beans that could be
 * affected by its mappings have been instantiated, the sheet must 
 * be 'primed' with respect to the bean factory. This step lets
 * every target of the overlay values know the full bean-path(s) 
 * that it should check for overlays. (Otherwise, beans -- especially
 * unnamed inner beans -- may not know the full-paths that lead to
 * their properties.) Also, this step catches in advance type 
 * mismatches, or attempts to overlay non-overlayable properties. 
 *
 */
public class Sheet implements BeanFactoryAware, BeanNameAware {
    private static final long serialVersionUID = 9129011082185864377L;
    
    /**
     * unique name of this Sheet; if Sheet has a beanName from original
     * configuration, that is always the name -- but the name might 
     * also be another string, in the case of Sheets added after 
     * initial container wiring
     */
    String name; 
    BeanFactory beanFactory; 
    /** map of full property-paths (from BeanFactory to individual 
     * property) and their changed value when this Sheet of overrides
     * is in effect
     */
    Map<String,Object> map = new ConcurrentHashMap<String, Object>(); 
    
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
    public void setBeanName(String name) {
        this.name = name; 
    }
    public void setName(String name) {
        this.name = name; 
    }
    public String getName() {
        return name; 
    }

    /**
     * Return map of full bean-path (starting with a target bean-name)
     * to the alternate value for that targeted property
     * @return Map<String,Object>
     */
    public Map<String, Object> getMap() {
        return map;
    }
    /**
     * Set map of property full bean-path (starting with a target 
     * bean-name) to alternate values. Note: provided map is copied 
     * into a local concurrent map, rather than used directly. 
     * @param m
     */
    @Required
    public void setMap(Map<String, Object> m) {
        this.map.clear();
        this.map.putAll(m);
    }
    
    /**
     * Ensure any properties targetted by this Sheet know to 
     * check the right property paths for overrides at lookup time,
     * and that the override values are compatible types for their 
     * destination properties. 
     * 
     * Should be done as soon as all possible targets are 
     * constructed (ApplicationListener ContextRefreshedEvent)
     * 
     * TODO: consider if  an 'un-priming' also needs to occur to 
     * prevent confusing side-effects. 
     * TODO: consider if priming should move to another class
     */
    public void prime() {
        for (String fullpath : map.keySet()) {
            int lastDot =  fullpath.lastIndexOf(".");
            String beanPath = fullpath.substring(0,lastDot);
            String terminalProp = fullpath.substring(lastDot+1);
            Object value = map.get(fullpath); 
            int i = beanPath.indexOf(".");
            Object bean; 
            HasKeyedProperties hkp;
            if (i < 0) {
                bean = beanFactory.getBean(beanPath);
            } else {
                String beanName = beanPath.substring(0,i);
                String propPath = beanPath.substring(i+1);
                BeanWrapperImpl wrapper = new BeanWrapperImpl(beanFactory.getBean(beanName));
                bean = wrapper.getPropertyValue(propPath);  
            }
            try {
                hkp = (HasKeyedProperties) bean;
            } catch (ClassCastException cce) {
                // targetted bean has no overridable properties
                throw new TypeMismatchException(bean,HasKeyedProperties.class,cce);
            }
            // install knowledge of this path 
            hkp.getKeyedProperties().addExternalPath(beanPath);
            // verify type-compatibility
            BeanWrapperImpl wrapper = new BeanWrapperImpl(hkp);
            Class<?> requiredType = wrapper.getPropertyType(terminalProp);
            try {
                // convert for destination type
                map.put(fullpath, wrapper.convertForProperty(value,terminalProp));
            } catch(TypeMismatchException tme) {
                TypeMismatchException tme2 = 
                    new TypeMismatchException(
                            new PropertyChangeEvent(
                                    hkp,
                                    fullpath,
                                    wrapper.getPropertyValue(terminalProp),
                                    value), requiredType, tme);
                throw tme2;
            }
        }
    }
}
