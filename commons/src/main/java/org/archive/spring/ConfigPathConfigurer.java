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
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.archive.io.ReadSource;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * Bean to fixup all configuration-relative ConfigPath instances, and
 * maintain an inventory of referenced paths. 
 * 
 * @contributor gojomo
 */
public class ConfigPathConfigurer 
    implements BeanPostProcessor, ApplicationContextAware {
    
    //// BEANPOSTPROCESSOR IMPLEMENTATION
    /**
     * Fix all beans with ConfigPath properties that lack a base path
     * or a name, to use a job-implied base path and name. 
     * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
     */
    public Object postProcessAfterInitialization(Object bean, String beanName)
    throws BeansException {
        fixupPaths(bean, beanName);
        return bean;
        
    }
    
    protected Object fixupPaths(Object bean, String beanName) {
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);
        for(PropertyDescriptor d : wrapper.getPropertyDescriptors()) {
            if(ConfigPath.class.isAssignableFrom(d.getPropertyType())
                || ReadSource.class.isAssignableFrom(d.getPropertyType())) {
                Object value = wrapper.getPropertyValue(d.getName());
                if(ConfigPath.class.isInstance(value)) {
                    ConfigPath cp = (ConfigPath) value;
                    if(cp==null) {
                        continue;
                    }
                    if(cp.getBase()==null) {
                        cp.setBase(path);
                    }
                    if(StringUtils.isEmpty(cp.getName())) {
                        cp.setName(beanName+"."+d.getName());
                    }
                    remember(cp);
                }
            }
        }
        return bean;
    }
    
    //// BEAN PROPERTIES
    
    /** 'home' directory for all other paths to be resolved 
     * relative to; defaults to directory of primary XML config file */
    ConfigPath path; 
    public ConfigPath getPath() {
        return path;
    }
    public void setPath(ConfigPath p) {
        path = p; 
    }
    
    //// APPLICATIONCONTEXTAWARE IMPLEMENTATION

    AbstractApplicationContext appCtx;
    /**
     * Remember ApplicationContext, and if possible primary 
     * configuration file's home directory. 
     * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
     */
    public void setApplicationContext(ApplicationContext appCtx) throws BeansException {
        this.appCtx = (AbstractApplicationContext)appCtx;
        String basePath;
        if(appCtx instanceof PathSharingContext) {
            String primaryConfigurationPath = ((PathSharingContext)appCtx).getPrimaryConfigurationPath();
            if(primaryConfigurationPath.startsWith("file:")) {
                // strip URI-scheme if present (as is usual)
                primaryConfigurationPath = primaryConfigurationPath.substring(5);
            }
            File configFile = new File(primaryConfigurationPath);
            basePath = configFile.getParent();
        } else {
            basePath = ".";
        }
        path = new ConfigPath("job base",basePath); 
    }

    // REMEMBERED PATHS
    Map<String,ConfigPath> paths = new HashMap<String,ConfigPath>();
    protected void remember(ConfigPath cp) {
        paths.put(cp.getName(), cp);
    }
    public Map<String,ConfigPath> getPaths() {
        return paths; 
    }
    
    // noop
    public Object postProcessBeforeInitialization(Object bean, String beanName) 
    throws BeansException {
        return bean;
    }
}
