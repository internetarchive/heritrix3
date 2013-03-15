package org.archive.crawler.restlet.models;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;

@SuppressWarnings("serial")
public class BeansModel extends HashMap<String, Object> {

    public BeansModel(String crawlJobShortName, String crawlJobUrl, String beanPath, Object bean, boolean editable, String problem, Object target, Collection<Object> allNamedCrawlBeans){
        super();
        this.put("crawlJobShortName",crawlJobShortName);
        this.put("crawlJobUrl",crawlJobUrl);
        this.put("beanPath", beanPath);
        this.put("bean",bean);
        this.put("editable",editable);
        this.put("problem", problem);
        this.put("target",target);
        this.put("allNamedCrawlBeans", allNamedCrawlBeans);
        
    }
}