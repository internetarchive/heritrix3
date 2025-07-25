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
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.archive.crawler.restlet.models.BeansModel;
import org.archive.crawler.restlet.models.ViewModel;
import org.archive.spring.PathSharingContext;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.WriterRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.representation.Variant;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;

/**
 * Restlet Resource which allows browsing the constructed beans in
 * a hierarchical fashion. 
 * 
 * @author gojomo
 * @author nlevitt
 * @author adam-miller
 * 
 */
public class BeanBrowseResource extends JobRelatedResource {
    protected PathSharingContext appCtx; 
    protected String beanPath;

    @Override
    public void init(Context ctx, Request req, Response res) throws ResourceException {
        super.init(ctx, req, res);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        getVariants().add(new Variant(MediaType.APPLICATION_XML));
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

    @Override
    protected Representation post(Representation entity, Variant variant) throws ResourceException {
        if (appCtx == null) {
            throw new ResourceException(404);
        }

        // copy op?
        Form form = new Form(entity);
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
        return new EmptyRepresentation();
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

    @Override
    public Representation get(Variant variant) throws ResourceException {
        if (appCtx == null) {
            throw new ResourceException(404);
        }

        if (variant.getMediaType() == MediaType.APPLICATION_XML) {
            return new WriterRepresentation(MediaType.APPLICATION_XML) {
                public void write(Writer writer) throws IOException {
                    XmlMarshaller.marshalDocument(writer, "beans", makeDataModel());
                }
            };
        } else {
            ViewModel viewModel = new ViewModel();
            viewModel.put("model", makeDataModel());
            return render("Beans.ftl", viewModel);
        }
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
}
