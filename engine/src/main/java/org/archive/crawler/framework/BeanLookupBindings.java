package org.archive.crawler.framework;

import java.util.Map;

import javax.script.SimpleBindings;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

/**
 * Provides syntactic sugar for H3 scripts to reference beans without adding a
 * line like {@code def scope = appCtx.getBean("scope");}. Instead, the script
 * may simply reference {@code scope}. Caveat: access to beans is read only.
 * 
 * @contributor travis
 */
public class BeanLookupBindings extends SimpleBindings {

    private final ApplicationContext appCtx;

    public BeanLookupBindings(ApplicationContext appCtx) {
        if (appCtx == null) throw new NullPointerException("appCtx");
        this.appCtx = appCtx;
    }

    public BeanLookupBindings(ApplicationContext appCtx, Map<String, Object> m) {
        super(m);
        if (appCtx == null) throw new NullPointerException("appCtx");
        this.appCtx = appCtx;
    }
    
    @Override
    public Object get(Object key) {
        Object ret = super.get(key);
        if (ret == null && key instanceof String) {
            try {
                ret = appCtx.getBean((String) key);
            } catch (BeansException e) {}
        }
        return ret;
    }

    @Override
    public boolean containsKey(Object key) {
        try {
            return super.containsKey(key) || appCtx.containsBean((String) key);
        } catch (Exception e) {
            return false;
        }
    }
}
