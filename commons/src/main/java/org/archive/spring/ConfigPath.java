/* This file is part of the Heritrix web crawler (crawler.archive.org).
 * 
 * Heritrix is free software!
 * 
 * Copyright 2008, Internet Archive Heritrix Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *   
 * $Header$
 */
package org.archive.spring;

import java.io.File;
import java.io.Serializable;

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
import org.springframework.beans.factory.annotation.Required;

/**
 * A filesystem path, as a bean, for the convenience of configuration
 * via srping beans.xml or user interfaces to same. 
 * 
 * Adds an optional relative-to base path and symbolic handle. 
 * 
 * See also ConfigPath
 */
public class ConfigPath implements Serializable {
    private static final long serialVersionUID = 1L;
    
    protected String name;
    protected String path; 
    protected ConfigPath base;
    
    public ConfigPath() {
        super();
    }

    public ConfigPath(String name, String path) {
        super();
        this.name = name;
        this.path = path;
    }

    public ConfigPath getBase() {
        return base;
    }

    public void setBase(ConfigPath base) {
        this.base = base;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    @Required
    public void setPath(String path) {
        this.path = path;
    } 
    
    public File getFile() {
        String interpolatedPath;
        if (configurer != null) {
            interpolatedPath = configurer.interpolate(path);
        } else {
            interpolatedPath = path;
        }
        
        return base == null || interpolatedPath.startsWith("/")
            ? new File(interpolatedPath) 
            : new File(base.getFile(), interpolatedPath);
    }
    
    /**
     * To maintain ConfigPath's 'base' and object-identity, this merge
     * should be used to updated ConfigPath properties in other beans, 
     * rather than discarding the old value. 
     * 
     * @param newvals ConfigPath to merge into this one
     * @return this
     */
    public ConfigPath merge(ConfigPath newvals) {
        if(newvals.name!=null) {
            setName(newvals.getName()); 
        }
        if(newvals.path!=null) {
            setPath(newvals.getPath()); 
        }
        return this; 
    }

    protected ConfigPathConfigurer configurer; 
    public void setConfigurer(ConfigPathConfigurer configPathConfigurer) {
        this.configurer = configPathConfigurer;
    }

}
