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
 
package org.archive.crawler.restlet;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.framework.Engine;
import org.restlet.Context;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.ResourceException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.InvalidPropertyException;

/**
 * Shared superclass for resources that represent functional aspects
 * of a CrawlJob.
 * 
 * @contributor Gojomo
 * @contributor nlevitt
 */
public abstract class JobRelatedResource extends BaseResource {
    CrawlJob cj; 

    IdentityHashMap<Object, String> beanToNameMap;
    
    public JobRelatedResource(Context ctx, Request req, Response res) throws ResourceException {
        super(ctx, req, res);
        cj = getEngine().getJob((String)req.getAttributes().get("job"));
        if(cj==null) {
            throw new ResourceException(404);
        }
    }
    
    protected Engine getEngine() {
        return ((EngineApplication)getApplication()).getEngine();
    }

    /**
     * Starting at (and including) the given object, adds nested Map
     * representations of named beans to the {@code namedBeans} Collection. The
     * nested Map representations are particularly suitable for use with with
     * {@link XmlMarshaller}.
     * 
     * @param namedBeans
     *            the Collection to add to
     * @param obj
     *            object to make a presentable Map for, if it has a beanName
     * @param alreadyWritten
     *            Set of objects already made presentable whose addition to
     *            {@code namedBeans} should be suppressed
     */
    protected void addPresentableNestedNames(Collection<Object> namedBeans, Object obj,
            Set<Object> alreadyWritten) {
        if (obj == null || alreadyWritten.contains(obj)
                || obj.getClass().getName().startsWith("org.springframework.")) {
            return;
        }

        Reference baseRef = getRequest().getResourceRef().getBaseRef();

        if (getBeanToNameMap().containsKey(obj)) {
            // this object is itself a named bean
            Map<String, Object> bean = new LinkedHashMap<String, Object>();
            bean.put("name", getBeanToNameMap().get(obj));
            bean.put("url", new Reference(baseRef, "../beans/" + getBeanToNameMap().get(obj)).getTargetRef());
            bean.put("class", obj.getClass().getName());

            namedBeans.add(bean);

            // nest children
            namedBeans = new LinkedList<Object>();
            bean.put("children", namedBeans);
        }
        
        if (!alreadyWritten.contains(obj)) {
            alreadyWritten.add(obj);

            BeanWrapperImpl bwrap = new BeanWrapperImpl(obj);
            for (PropertyDescriptor pd : getPropertyDescriptors(bwrap)) {
                if (pd.getReadMethod() != null) {
                    String propName = pd.getName();
                    Object propValue = bwrap.getPropertyValue(propName);
                    addPresentableNestedNames(namedBeans, propValue, alreadyWritten);
                }
            }
            if (obj.getClass().isArray()) {
                List<?> list = Arrays.asList(obj);
                for (int i = 0; i < list.size(); i++) {
                    addPresentableNestedNames(namedBeans, list.get(i),
                            alreadyWritten);
                }
            }
            if (obj instanceof Iterable<?>) {
                for (Object next : (Iterable<?>) obj) {
                    addPresentableNestedNames(namedBeans, next, alreadyWritten);
                }
            }
        }
    }
    
    /**
     * Starting at (and including) the given object, write a hierarchical
     * list of named beans in HTML to the PrintWriter. 
     * 
     * @param pw PrintWriter
     * @param obj object to write, if it has a beanName, as a <LI>
     * @param prefix URI prefix to apply before names for browse links
     * @param alreadyWritten Set of already-written objects whose display 
     * should be suppressed
     */
    @SuppressWarnings("unchecked")
    protected void writeNestedNames(PrintWriter pw, Object obj, String prefix, Set<Object> alreadyWritten) {
        // don't consider nulls, objects already shown, or spring classes
        if(obj==null 
            || alreadyWritten.contains(obj) 
            || obj.getClass().getName().startsWith("org.springframework.")) {
            return;
        }
        
        String close = "";
        if(getBeanToNameMap().containsKey(obj)) {        
            String name = getBeanToNameMap().get(obj);
            pw.println("<li><a href='"+prefix+name+"'>"+name+"</a>");
            pw.println("<span style='color:#999'>"+obj.getClass().getName()+"</span><ul>");
            close = "</ul></li>";
        }
        if(!alreadyWritten.contains(obj)) {
            alreadyWritten.add(obj);
            try {
                BeanWrapperImpl bwrap = new BeanWrapperImpl(obj); 
                for(PropertyDescriptor pd : getPropertyDescriptors(bwrap)) {
                    if(pd.getReadMethod()!=null) {
                        String propName = pd.getName();
                        writeNestedNames(pw, bwrap.getPropertyValue(propName), prefix, alreadyWritten);
                    } 
                }
                if(obj.getClass().isArray()) {
                    List<?> list = Arrays.asList(obj);
                    for(int i = 0; i < list.size(); i++) {
                        writeNestedNames(pw, list.get(i), prefix, alreadyWritten);
                    }
                }
                if(obj instanceof Iterable) {
                    for (Object next : (Iterable)obj) {
                        writeNestedNames(pw, next, prefix, alreadyWritten);
                    }
                }
            } catch (InvalidPropertyException ipe) {
                pw.println("<span style='color:red'>"+ipe.getMessage()+"</span>");
            }
        }
        pw.println(close);
    }


    /**
     * Write an HTML representation of the given object to the PrintWriter. 
     * 
     * @param pw PrintWriter
     * @param field field name to display for object
     * @param object object to write
     */
    protected void writeObject(PrintWriter pw, String field, Object object) {
        writeObject(pw, field, object, new HashSet<Object>(), null);
    }

    /**
     * Write an HTML representation of the given object to the PrintWriter. 
     * 
     * @param pw PrintWriter
     * @param field field name to display for object
     * @param object object to write
     * @param beanPath beanPath prefix to apply to sub fields browse links
     */
    protected void writeObject(PrintWriter pw, String field, Object object, String beanPath) {
        writeObject(pw, field, object, new HashSet<Object>(), beanPath);
    }

    /**
     * Constructs a nested Map data structure of the information represented
     * by {@code object}. The result is particularly suitable for use with with
     * {@link XmlMarshaller}.
     * 
     * @param field
     *            field name for object
     * @param object
     *            object to make presentable map for
     * @return the presentable Map
     */
    protected Map<String, Object> makePresentableMapFor(String field, Object object) {
        return makePresentableMapFor(field, object, new HashSet<Object>(), null);
    }

    /**
     * Constructs a nested Map data structure of the information represented
     * by {@code object}. The result is particularly suitable for use with with
     * {@link XmlMarshaller}.
     * 
     * @param field
     *            field name for object
     * @param object
     *            object to make presentable map for
     * @param beanPathPrefix
     *            beanPath prefix to apply to sub fields browse links
     * @return the presentable Map
     */
    protected Map<String, Object> makePresentableMapFor(String field, Object object, String beanPath) {
        return makePresentableMapFor(field, object, new HashSet<Object>(), beanPath);
    }

    /**
     * Constructs a nested Map data structure of the information represented
     * by {@code object}. The result is particularly suitable for use with with
     * {@link XmlMarshaller}.
     * 
     * @param field
     *            field name for object
     * @param object
     *            object to make presentable map for
     * @param alreadyWritten
     *            Set of objects already made presentable whose addition to the
     *            Map should be suppressed
     * @param beanPathPrefix
     *            beanPath prefix to apply to sub fields browse links
     * @return the presentable Map
     */
    protected Map<String, Object> makePresentableMapFor(String field, Object object, HashSet<Object> alreadyWritten, String beanPathPrefix) {
        Map<String,Object> info = new LinkedHashMap<String, Object>();
        Reference baseRef = getRequest().getResourceRef().getBaseRef();

        String beanPath = beanPathPrefix;

        if(StringUtils.isNotBlank(field)) {
            info.put("field", field);

            if(StringUtils.isNotBlank(beanPathPrefix)) {
                if(beanPathPrefix.endsWith(".")) {
                    beanPath += field;
                } else if (beanPathPrefix.endsWith("[")) {
                    beanPath += field + "]";
                }
                info.put("url", new Reference(baseRef, "../beans/" + beanPath).getTargetRef());
            }
        }
        String key = getBeanToNameMap().get(object);

        if (object == null) {
            info.put("propValue", null);
            return info;
        }
        if (object instanceof String || BeanUtils.isSimpleValueType(object.getClass()) || object instanceof File) {
            info.put("class", object.getClass().getName());
            info.put("propValue", object);
            return info;
        }
        if (alreadyWritten.contains(object)) {
            info.put("propValuePreviouslyDescribed", null);
            return info;
        }

        alreadyWritten.add(object); // guard against repeats and cycles

        if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(field)) {
            info.put("key", key);
            info.put("url", new Reference(baseRef, "../beans/" + key).getTargetRef());
            return info;
        }
        
        info.put("class", object.getClass().getName());

        Collection<Object> properties = new LinkedList<Object>();
        BeanWrapperImpl bwrap = new BeanWrapperImpl(object);
        for (PropertyDescriptor pd : getPropertyDescriptors(bwrap)) {

            if (pd.getReadMethod() != null && !pd.isHidden()) {
                String propName = pd.getName();
                if (beanPath != null) {
                    beanPathPrefix = beanPath + ".";
                }
                Object propValue = makePresentableMapFor(propName, 
                        bwrap.getPropertyValue(propName), 
                        alreadyWritten, beanPathPrefix);
                properties.add(propValue);
            }
        }
        if (properties.size() > 0) {
            info.put("properties", properties);
        }
        
        Collection<Object> propValues = new LinkedList<Object>();
        if(object.getClass().isArray()) {
            Object[] array = (Object[])object;
            for(int i = 0; i < array.length; i++) {
                if(beanPath!=null) {
                    beanPathPrefix = beanPath+"[";
                }
                // TODO: protect against overlong content? 
                propValues.add(makePresentableMapFor(i + "", array[i],
                        alreadyWritten, beanPathPrefix));
            }
        }
        if (object instanceof List<?>) {
            List<?> list = (List<?>) object;
            for (int i = 0; i < list.size(); i++) {
                if (beanPath != null) {
                    beanPathPrefix = beanPath + "[";
                }
                // TODO: protect against overlong content?
                propValues.add(makePresentableMapFor(i + "", list.get(i),
                        alreadyWritten, beanPathPrefix));
            }
        } else if (object instanceof Iterable<?>) {
            for (Object next : (Iterable<?>) object) {
                propValues.add(makePresentableMapFor("#", next, alreadyWritten,
                        beanPathPrefix));
            }
        }
        if (object instanceof Map<?,?>) {
            for (Object next : ((Map<?,?>) object).entrySet()) {
                // TODO: protect against giant maps?
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) next;
                if (beanPath != null) {
                    beanPathPrefix = beanPath + "[";
                }
                propValues.add(makePresentableMapFor(entry.getKey().toString(),
                        entry.getValue(), alreadyWritten, beanPathPrefix));
            }
        }
        if (propValues.size() > 0) {
            info.put("propValue", propValues);
        }

        return info;
    }

    /**
     * Get and modify the PropertyDescriptors associated with the BeanWrapper.
     * @param bwrap
     * @return
     */
    protected PropertyDescriptor[] getPropertyDescriptors(BeanWrapperImpl bwrap) {
        PropertyDescriptor[] descriptors = bwrap.getPropertyDescriptors();
        for(PropertyDescriptor pd : descriptors) {
            if (DescriptorUpdater.class.isAssignableFrom(bwrap.getWrappedClass()) ) {
                ((DescriptorUpdater) bwrap.getWrappedInstance()).updateDescriptor(pd);
            } else {
                defaultUpdateDescriptor(pd);
            }
        }
        return descriptors;
    }

    /**
     * Get a map giving object beanNames.
     * 
     * @return map from object to beanName
     */
    private IdentityHashMap<Object, String> getBeanToNameMap() {
        if(beanToNameMap == null) {
            beanToNameMap = new IdentityHashMap<Object, String>();
            for(Object entryObj : cj.getJobContext().getBeansOfType(Object.class).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>)entryObj;
                beanToNameMap.put(entry.getValue(),(String)entry.getKey());
            }
        }
        return beanToNameMap;
    }
        
    /**
     * Write an HTML representation of the given object to the PrintWriter. 
     * 
     * @param pw PrintWriter
     * @param field field name to display for object
     * @param object object to write
     * @param alreadyWritten Set of objects to not redundantly write
     * @param beanPathPrefix beanPath prefix to apply to sub fields browse links
     */
    @SuppressWarnings("unchecked")
    protected void writeObject(PrintWriter pw, String field, Object object, HashSet<Object> alreadyWritten, String beanPathPrefix) {
            String key = getBeanToNameMap().get(object);
            String close = "";
            String beanPath = beanPathPrefix;
            if(StringUtils.isNotBlank(field)) {
                pw.write("<tr><td align='right' valign='top'><b>");
                String closeAnchor ="";
                if(StringUtils.isNotBlank(beanPathPrefix)) {
                    if(beanPathPrefix.endsWith(".")) {
                        beanPath += field;
                    } else if (beanPathPrefix.endsWith("[")) {
                        beanPath += field + "]";
                    }
                    pw.println("<a href='../beans/"+beanPath+"'>");
                    closeAnchor = "</a>";
                }
                pw.write(field);
                pw.write(closeAnchor);
                pw.write(":</b></td><td>");
                close="</td></tr>";
            }
            if(object == null) {
                pw.write("<i>null</i><br/>");
                pw.write(close);
                return; 
            }
            if(object instanceof String) {
                pw.write("\""+object+"\"<br/>");
                pw.write(close);
                return; 
            }
            if(BeanUtils.isSimpleValueType(object.getClass()) 
                    || object instanceof File
                    //|| BeanUtils.findEditorByConvention(object.getClass())!=null
                    ) {     
                pw.write(object+"<br/>");
                pw.write(close);
                return;
            }
            if(alreadyWritten.contains(object)) {
                pw.println("&uarr;<br/>");
                pw.write(close);
                return;
            }
            alreadyWritten.add(object); // guard against repeats and cycles
            if(StringUtils.isNotBlank(key) && StringUtils.isNotBlank(field)) {
                pw.println("<a href='../beans/"+key+"'>"+key+"</a><br/>");
                pw.write(close);
                return;
            }
            pw.print("<fieldset style='display:inline;vertical-align:top'><legend>");

            pw.println(object.getClass().getName()+"</legend>");
            pw.println("<table>");
            BeanWrapperImpl bwrap = new BeanWrapperImpl(object); 
            for(PropertyDescriptor pd : getPropertyDescriptors(bwrap)) {
                if(pd.getReadMethod()!=null && !pd.isHidden()) {
                    String propName = pd.getName();
                    if(beanPath!=null) {
                        beanPathPrefix = beanPath+".";
                    }
                    writeObject(pw, propName, bwrap.getPropertyValue(propName), alreadyWritten, beanPathPrefix);
                } 
            }
            if(object.getClass().isArray()) {
                Object[] array = (Object[])object;
                for(int i = 0; i < array.length; i++) {
                    if(beanPath!=null) {
                        beanPathPrefix = beanPath+"[";
                    }
                    // TODO: protect against overlong content? 
                    writeObject(pw, i+"", array[i], alreadyWritten, beanPathPrefix);
                }
            }
            if(object instanceof List) {
                List<?> list = (List<?>)object;
                for(int i = 0; i < list.size(); i++) {
                    if(beanPath!=null) {
                        beanPathPrefix = beanPath+"[";
                    }
                    // TODO: protect against overlong content? 
                    writeObject(pw, i+"", list.get(i), alreadyWritten, beanPathPrefix);
                }
            } else if(object instanceof Iterable) {
                for (Object next : (Iterable)object) {
                    writeObject(pw, "#", next, alreadyWritten, null);
                }
            }
            if(object instanceof Map) {
                for (Object next : ((Map)object).entrySet()) {
                    // TODO: protect against giant maps?
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>)next;
                    if(beanPath!=null) {
                        beanPathPrefix = beanPath+"[";
                    }
                    writeObject(pw, entry.getKey().toString(), entry.getValue(), alreadyWritten, beanPathPrefix);
                }
            }
            pw.println("</table>");
            pw.print("</fieldset><br/>");
            pw.write(close);
        }

    /** suppress problematic properties */
    static HashSet<String> HIDDEN_PROPS = new HashSet<String>(
            Arrays.asList(new String[]
             {"class","declaringClass","keyedProperties","running","first","last","empty", "inbound", "outbound", "cookiesMap"}
            ));
    protected void defaultUpdateDescriptor(PropertyDescriptor pd) {
        // make non-editable
        try {
            pd.setWriteMethod(null);
        } catch (IntrospectionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if(HIDDEN_PROPS.contains(pd.getName())) {
            pd.setHidden(true);
        }
    }
}
