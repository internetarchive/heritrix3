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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.archive.spring.PathSharingContext;
import org.archive.util.TextUtils;
import org.restlet.Context;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.restlet.resource.WriterRepresentation;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;

/**
 * Restlet Resource which allows browsing the constructed beans in
 * a hierarchical fashion. 
 * 
 * @contributor gojomo
 * @contributor nlevitt
 */
public class BeanBrowseResource extends JobRelatedResource {
    PathSharingContext appCtx; 
    String beanPath; 
    
    public BeanBrowseResource(Context ctx, Request req, Response res) throws ResourceException {
        super(ctx, req, res);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        getVariants().add(new Variant(MediaType.APPLICATION_XML));
        setModifiable(true); // accept POSTs
        appCtx = cj.getJobContext();
        beanPath = (String)req.getAttributes().get("beanPath");
        if (beanPath!=null) {
            try {
                beanPath = URLDecoder.decode(beanPath,"UTF-8");
            } catch (UnsupportedEncodingException e) {
                // inconceivable! UTF-8 required all Java impls
            }
        } else {
            beanPath = "";
        }
    }

    public void acceptRepresentation(Representation entity) throws ResourceException {
        if (appCtx == null) {
            throw new ResourceException(404);
        }

        // copy op?
        Form form = getRequest().getEntityAsForm();
        beanPath = form.getFirstValue("beanPath");
        
        String newVal = form.getFirstValue("newVal");
        if(newVal!=null) {
            int i = beanPath.indexOf(".");
            String beanName = i<0?beanPath:beanPath.substring(0,i);
            Object namedBean = appCtx.getBean(beanName);
            BeanWrapperImpl bwrap = new BeanWrapperImpl(namedBean);
            String propPath = beanPath.substring(i+1);
            Object coercedVal = bwrap.convertIfNecessary(newVal, bwrap.getPropertyValue(propPath).getClass()); 
            bwrap.setPropertyValue(propPath, coercedVal);
        }
        Reference ref = getRequest().getResourceRef();
        ref.setPath(getBeansRefPath());
        ref.addSegment(beanPath);
        getResponse().redirectSeeOther(ref);
    }

    public String getBeansRefPath() {
        Reference ref = getRequest().getResourceRef();
        String path = ref.getPath(); 
        int i = path.indexOf("/beans/");
        if(i>0) {
            return path.substring(0,i+"/beans/".length());
        }
        if(!path.endsWith("/")) {
            path += "/";
        }
        return path; 
    }

    public Representation represent(Variant variant) throws ResourceException {
        if (appCtx == null) {
            throw new ResourceException(404);
        }

        Representation representation;
        if (variant.getMediaType() == MediaType.APPLICATION_XML) {
            representation = new WriterRepresentation(MediaType.APPLICATION_XML) {
                public void write(Writer writer) throws IOException {
                    XmlMarshaller.marshalDocument(writer, "script", makePresentableMap());
                }
            };
        } else {
            representation = new WriterRepresentation(
                    MediaType.TEXT_HTML) {
                public void write(Writer writer) throws IOException {
                    BeanBrowseResource.this.writeHtml(writer);
                }
            };
        }
        // TODO: remove if not necessary in future?
        representation.setCharacterSet(CharacterSet.UTF_8);
        return representation;
    }
    
    /**
     * Constructs a nested Map data structure with the information represented
     * by this Resource. The result is particularly suitable for use with with
     * {@link XmlMarshaller}.
     * 
     * @return the nested Map data structure
     */
    protected LinkedHashMap<String,Object> makePresentableMap() {
        LinkedHashMap<String,Object> info = new LinkedHashMap<String,Object>();

        info.put("crawlJobShortName", cj.getShortName());
        info.put("crawlJobUrl", new Reference(getRequest().getResourceRef().getBaseRef(), "..").getTargetRef());
        
        if (StringUtils.isNotBlank(beanPath)) {
            info.put("beanPath", beanPath);
            try {
                int firstDot = beanPath.indexOf(".");
                String beanName = firstDot<0?beanPath:beanPath.substring(0,firstDot);
                Object namedBean = appCtx.getBean(beanName);
                Object target; 
                if (firstDot < 0) {
                    target = namedBean;
                    info.put("bean", makePresentableMapFor(null, target, beanPath));
                } else {
                    BeanWrapperImpl bwrap = new BeanWrapperImpl(namedBean);
                    String propPath = beanPath.substring(firstDot+1);
                    target = bwrap.getPropertyValue(propPath);
                    
                    Class<?> type = bwrap.getPropertyType(propPath);
                    if(bwrap.isWritableProperty(propPath) 
                            && (bwrap.getDefaultEditor(type)!=null|| type == String.class)
                            && !Collection.class.isAssignableFrom(type)) {
                        info.put("editable", true);
                        info.put("bean", makePresentableMapFor(null, target));
                    } else {
                        info.put("bean", makePresentableMapFor(null, target, beanPath));
                    }
                }     
            } catch (BeansException e) {
                info.put("problem", e.toString());
            }
        }

        Collection<Object> nestedNames = new LinkedList<Object>();
        Set<Object> alreadyWritten = new HashSet<Object>();
        addPresentableNestedNames(nestedNames, appCtx.getBean("crawlController"), alreadyWritten);
        for(String name: appCtx.getBeanDefinitionNames()) {
            addPresentableNestedNames(nestedNames, appCtx.getBean(name), alreadyWritten);
        }
        info.put("allNamedCrawlBeans", nestedNames);

        return info;
    }

    protected void writeHtml(Writer writer) {
        PrintWriter pw = new PrintWriter(writer); 
        pw.println("<head><title>Crawl beans in "+cj.getShortName()+"</title></head>");
        pw.println("<h1>Crawl beans in built job <i><a href='/engine/job/"
                +TextUtils.urlEscape(cj.getShortName())
                +"'>"+cj.getShortName()+"</a></i></h1>");
        pw.println("Enter a bean path of the form <i>beanName</i>, <i>beanName.property</i>, <i>beanName.property[indexOrKey]</i>, etc.");
        pw.println("<form method='POST'><input type='text' name='beanPath' style='width:400px' value='"+beanPath+"'/>");
        pw.println("<input type='submit' value='view'/></form>");
        
        if (StringUtils.isNotBlank(beanPath)) {
            pw.println("<h2>Bean path <i>"+beanPath+"</i></h2>");
            try {
                int i = beanPath.indexOf(".");
                String beanName = i<0?beanPath:beanPath.substring(0,i);
                Object namedBean = appCtx.getBean(beanName);
                Object target; 
                if (i<0) {
                    target = namedBean;
                    writeObject(pw, null, target, beanPath);
                } else {
                    BeanWrapperImpl bwrap = new BeanWrapperImpl(namedBean);
                    String propPath = beanPath.substring(i+1);
                    target = bwrap.getPropertyValue(propPath);
                    
                    Class<?> type = bwrap.getPropertyType(propPath);
                    if(bwrap.isWritableProperty(propPath) 
                            && (bwrap.getDefaultEditor(type)!=null|| type == String.class)
                            && !Collection.class.isAssignableFrom(type)) {
                        pw.println(beanPath+" = ");
                        writeObject(pw, null, target);
                        pw.println("<a href=\"javascript:document.getElementById('editform').style.display='inline';void(0);\">edit</a>");
                        pw.println("<span id='editform' style=\'display:none\'>Note: it may not be appropriate/effective to change this value in an already-built crawl context.<br/>");
                        pw.println("<form  id='editform' method='POST'>");
                        pw.println("<input type='hidden' name='beanPath' value='"+beanPath+"'/>");
                        pw.println(beanPath + " = <input type='text' name='newVal' style='width:400px' value='"+target+"'/>");
                        pw.println("<input type='submit' value='update'/></form></span>");
                    } else {
                        writeObject(pw, null, target, beanPath);
                    }
                }       
            } catch (BeansException e) {
                pw.println("<i style='color:red'>problem: "+e.getMessage()+"</i>");
            }
        }
    
        pw.println("<h2>All named crawl beans</h2");
        pw.println("<ul>");
        Set<Object> alreadyWritten = new HashSet<Object>(); 
        writeNestedNames(pw, appCtx.getBean("crawlController"), getBeansRefPath(), alreadyWritten);
        for(String name : appCtx.getBeanDefinitionNames() ) {
            writeNestedNames(pw, appCtx.getBean(name), getBeansRefPath(), alreadyWritten);
        }
        pw.println("</ul>");
    }
}
