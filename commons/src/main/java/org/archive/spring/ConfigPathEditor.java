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

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.beans.PropertyChangeListener;
import java.beans.PropertyEditor;

/**
 * PropertyEditor allowing Strings to become ConfigPath instances.
 * 
 */
public class ConfigPathEditor implements PropertyEditor {
    protected Object value;
    
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getAsText() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Component getCustomEditor() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getJavaInitializationString() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String[] getTags() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object getValue() {
        ConfigPath c =  new ConfigPath(null,value.toString());
        //c.put(value);
        return c;
    }

    @Override
    public boolean isPaintable() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void paintValue(Graphics gfx, Rectangle box) {
        // TODO Auto-generated method stub

    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        setValue(text);
    }

    @Override
    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public boolean supportsCustomEditor() {
        // TODO Auto-generated method stub
        return false;
    }

}
