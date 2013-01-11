package org.archive.crawler.framework;

import java.util.Map;

import javax.script.SimpleBindings;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

/**
 * Provides syntactic sugar for H3 scripts to reference beans without adding a
 * line like {@code def scope = appCtx.getBean("scope");}. Instead, the script
 * may simply reference {@code scope}. Caveat: access is read only.
 * 
 * @contributor travis
 */
public class BeanLookupBindings extends SimpleBindings {

    private ApplicationContext appCtx = null;

    public BeanLookupBindings(ApplicationContext appCtx) {
        assert appCtx != null;
        this.appCtx = appCtx;
    }

    public BeanLookupBindings(ApplicationContext appCtx, Map<String, Object> m) {
        super(m);
        assert appCtx != null;
        this.appCtx = appCtx;
    }

    @Override
    public Object get(Object key) {
        if (key instanceof String) {
            try {
                Object ret = appCtx.getBean((String) key);
                if (ret != null) {
                    return ret;
                }
            } catch (BeansException e) {}
        }
        return super.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof String) {
            try {
                boolean ret = appCtx.containsBean((String) key);
                if (ret == true) {
                    return ret;
                }
            } catch (BeansException e) {}
        }
        return super.containsKey(key);
    }

    @Override
    public Object put(String name, Object value) {
        // restrict setting variables that conflict with bean names
        if (appCtx.containsBean(name)) {
            throw new IllegalArgumentException("name conflict: \""+ name +"\" is the name of a bean.");
        }
        return super.put(name, value);
    }
}
