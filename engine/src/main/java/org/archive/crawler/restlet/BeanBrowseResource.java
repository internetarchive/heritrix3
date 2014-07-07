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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.archive.crawler.restlet.models.BeansModel;
import org.archive.crawler.restlet.models.ViewModel;
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

import freemarker.template.Configuration;
import freemarker.template.ObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Restlet Resource which allows browsing the constructed beans in
 * a hierarchical fashion. 
 * 
 * @contributor gojomo
 * @contributor nlevitt
 * @contributor adam-miller
 * 
 */
public class BeanBrowseResource extends JobRelatedResource {
    protected PathSharingContext appCtx; 
    protected String beanPath; 
    private Configuration _templateConfiguration;
    
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
        
        Configuration tmpltCfg = new Configuration();
        tmpltCfg.setClassForTemplateLoading(this.getClass(),"");
        tmpltCfg.setObjectWrapper(ObjectWrapper.BEANS_WRAPPER);
        setTemplateConfiguration(tmpltCfg);
    }
    public void setTemplateConfiguration(Configuration tmpltCfg) {
        _templateConfiguration=tmpltCfg;
    }
    public Configuration getTemplateConfiguration(){
        return _templateConfiguration;
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
                    XmlMarshaller.marshalDocument(writer, "beans", makeDataModel());
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
    protected BeansModel makeDataModel(){
        Object bean=null;
        String problem=null;
        boolean editable=false;
        Object target=null;
        
        if (StringUtils.isNotBlank(beanPath)) {
            try {
                int firstDot = beanPath.indexOf(".");
                String beanName = firstDot<0?beanPath:beanPath.substring(0,firstDot);
                Object namedBean = appCtx.getBean(beanName);
                if (firstDot < 0) {
                    target = namedBean;
                    bean = makePresentableMapFor(null, target, beanPath);
                } else {
                    BeanWrapperImpl bwrap = new BeanWrapperImpl(namedBean);
                    String propPath = beanPath.substring(firstDot+1);
                    target = bwrap.getPropertyValue(propPath);
                    
                    Class<?> type = bwrap.getPropertyType(propPath);
                    if(bwrap.isWritableProperty(propPath) 
                            && (bwrap.getDefaultEditor(type)!=null|| type == String.class)
                            && !Collection.class.isAssignableFrom(type)) {
                        editable=true;
                        bean = makePresentableMapFor(null, target);
                    } else {
                        bean = makePresentableMapFor(null, target, beanPath);
                    }
                }     
            } catch (BeansException e) {
                problem = e.toString();
            }
        }

        Collection<Object> nestedNames = new LinkedList<Object>();
        Set<Object> alreadyWritten = new HashSet<Object>();
        addPresentableNestedNames(nestedNames, appCtx.getBean("crawlController"), alreadyWritten);
        for(String name: appCtx.getBeanDefinitionNames()) {
            addPresentableNestedNames(nestedNames, appCtx.getBean(name), alreadyWritten);
        }
        
        return new BeansModel(cj.getShortName(), 
                new Reference(getRequest().getResourceRef().getBaseRef(), "..").getTargetRef().toString(), 
                beanPath, 
                bean, 
                editable, 
                problem,
                target,
                nestedNames);

    }

    protected void writeHtml(Writer writer) {
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        Configuration tmpltCfg = getTemplateConfiguration();

        ViewModel viewModel = new ViewModel();
        viewModel.setFlashes(Flash.getFlashes(getRequest()));
        viewModel.put("baseRef",baseRef);
        viewModel.put("model",makeDataModel());

        try {
            Template template = tmpltCfg.getTemplate("Beans.ftl");
            template.process(viewModel, writer);
            writer.flush();
        } catch (IOException e) { 
            throw new RuntimeException(e); 
        } catch (TemplateException e) { 
            throw new RuntimeException(e); 
        }
        
    }
}
