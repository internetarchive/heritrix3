package org.archive.crawler.restlet.models;

import java.util.Collection;
import java.util.LinkedHashMap;

@SuppressWarnings("serial")
public class BeansModel extends LinkedHashMap<String, Object> {

    public BeansModel(String crawlJobShortName, String crawlJobUrl,
            String beanPath, Object bean, boolean editable, String problem,
            Object target, Collection<Object> allNamedCrawlBeans) {
        super();
        this.put("crawlJobShortName", crawlJobShortName);
        this.put("crawlJobUrl", crawlJobUrl);
        this.put("beanPath", beanPath);
        this.put("bean", bean);
        this.put("editable", editable);
        this.put("problem", problem);
        this.put("target", target);
        this.put("allNamedCrawlBeans", allNamedCrawlBeans);
    }
}