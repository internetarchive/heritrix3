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

import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;

/**
 * Bean to fixup all configuration-relative ConfigPath instances, and
 * maintain an inventory of referenced paths. 
 * 
 * For each bean, at BeanPostProcessor time, the bean is remembered for
 * later fixup. Then, at ApplicationListener ContextRefreshedEvent time, 
 * fixup occurs at the latest possible time. This allows other intervening 
 * Spring post-processors -- most notably, PropertyOverrideConfigurer -- to 
 * replace values before fixup, and fixup will still affect the right
 * final values. 
 * 
 * @contributor gojomo
 */
public class ConfigPathConfigurer 
implements 
    BeanPostProcessor, 
    ApplicationListener<ApplicationEvent>,
    ApplicationContextAware, 
    Ordered {
    private static final Logger logger =
        Logger.getLogger(ConfigPathConfigurer.class.getName());

    protected Map<String,Object> allBeans = new HashMap<String,Object>();
    
    
    //// BEAN PROPERTIES
    
    /** 'home' directory for all other paths to be resolved 
     * relative to; defaults to directory of primary XML config file */
    protected ConfigPath path; 
    public ConfigPath getPath() {
        return path;
    }
    
    //// BEANPOSTPROCESSOR IMPLEMENTATION
    /**
     * Remember all beans for later fixup.
     * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
     */
    public Object postProcessAfterInitialization(Object bean, String beanName)
    throws BeansException {
        allBeans.put(beanName,bean);
        return bean;
    }
    
    // APPLICATIONLISTENER IMPLEMENTATION
    /**
     * Fix all beans with ConfigPath properties that lack a base path
     * or a name, to use a job-implied base path and name. 
     */
    public void onApplicationEvent(ApplicationEvent event) {
        if(event instanceof ContextRefreshedEvent) {
            for(String beanName: allBeans.keySet()) {
                fixupPaths(allBeans.get(beanName), beanName);
            }
            allBeans.clear(); // forget 
        }
        // ignore all others
    }

    /**
     * Find any ConfigPath properties in the passed bean; ensure that
     * if they have a null 'base', that is replaced with the job home
     * directory. Also, remember all ConfigPaths so fixed-up for later
     * reference. 
     * 
     * @param bean
     * @param beanName
     * @return Same bean as passed in, fixed as necessary
     */
    protected Object fixupPaths(Object bean, String beanName) {
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);
        for(PropertyDescriptor d : wrapper.getPropertyDescriptors()) {
            if (d.getPropertyType().isAssignableFrom(ConfigPath.class)
                    || d.getPropertyType().isAssignableFrom(ConfigFile.class)) {
                Object value = wrapper.getPropertyValue(d.getName());
                if (value != null && value instanceof ConfigPath) {
                    String patchName = beanName+"."+d.getName();
                    fixupConfigPath((ConfigPath) value,patchName);
                }
            } else if (Iterable.class.isAssignableFrom(d.getPropertyType())) {
                Iterable<?> iterable = (Iterable<?>) wrapper.getPropertyValue(d.getName());
                if (iterable != null) {
                    int i = 0;
                    for (Object candidate : iterable) {
                        if(candidate!=null && candidate instanceof ConfigPath) {
                            String patchName = beanName+"."+d.getName()+"["+i+"]";
                            fixupConfigPath((ConfigPath) candidate,patchName); 
                        }
                        i++; 
                    }
                }
            }
        }
        return bean;
    }

    protected void fixupConfigPath(ConfigPath cp, String patchName) {
        if(cp.getBase()==null && cp != path) {
            cp.setBase(path);
        }
        
        if(StringUtils.isEmpty(cp.getName())) {
            cp.setName(patchName);
        }
        cp.setConfigurer(this);
        remember(patchName, cp);
    }

    //// APPLICATIONCONTEXTAWARE IMPLEMENTATION

    protected PathSharingContext appCtx;
    /**
     * Remember ApplicationContext, and if possible primary 
     * configuration file's home directory. 
     * 
     * Requires appCtx be a PathSharingContext 
     * 
     * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
     */
    public void setApplicationContext(ApplicationContext appCtx) throws BeansException {
        this.appCtx = (PathSharingContext)appCtx;
        String basePath;
        if(appCtx instanceof PathSharingContext) {
            basePath = this.appCtx.getConfigurationFile().getParent();
        } else {
            basePath = ".";
        }
        path = new ConfigPath("job base",basePath); 
        path.setConfigurer(this);
    }

    // REMEMBERED CONFIGPATHS
    protected Map<String,ConfigPath> allConfigPaths = new HashMap<String,ConfigPath>();
    protected void remember(String key, ConfigPath cp) {
        allConfigPaths.put(key, cp);
    }
    public Map<String,ConfigPath> getAllConfigPaths() {
        return allConfigPaths; 
    }
    
    // noop
    public Object postProcessBeforeInitialization(Object bean, String beanName) 
    throws BeansException {
        return bean;
    }

    /** 
     * Act as late as possible.
     * 
     * @see org.springframework.core.Ordered#getOrder()
     */
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    public void snapshotToLaunchDir(File readFile) throws IOException {
        if(appCtx.getCurrentLaunchDir()==null|| !appCtx.getCurrentLaunchDir().exists()) {
            logger.log(Level.WARNING, "launch directory unavailable to snapshot "+readFile); 
            return; 
        }
        FileUtils.copyFileToDirectory(readFile, appCtx.getCurrentLaunchDir());
    }

    protected String interpolate(String rawPath) {
        if (appCtx.getCurrentLaunchId() != null) {
            return rawPath.replace("${launchId}", appCtx.getCurrentLaunchId());
        } else {
            return rawPath;
        }
    }
}
