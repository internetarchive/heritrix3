package org.archive.spring;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.beans.PropertyChangeListener;
import java.beans.PropertyEditor;


public class ConfigPathEditor<T> implements PropertyEditor {
    Object value;
    
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        // TODO Auto-generated method stub

    }

    public String getAsText() {
        // TODO Auto-generated method stub
        return null;
    }

    public Component getCustomEditor() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getJavaInitializationString() {
        // TODO Auto-generated method stub
        return null;
    }

    public String[] getTags() {
        // TODO Auto-generated method stub
        return null;
    }

    public Object getValue() {
        ConfigPath c =  new ConfigPath(null,value.toString());
        //c.put(value);
        return c;
    }

    public boolean isPaintable() {
        // TODO Auto-generated method stub
        return false;
    }

    public void paintValue(Graphics gfx, Rectangle box) {
        // TODO Auto-generated method stub

    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        // TODO Auto-generated method stub

    }

    public void setAsText(String text) throws IllegalArgumentException {
        setValue(text);
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public boolean supportsCustomEditor() {
        // TODO Auto-generated method stub
        return false;
    }

}
