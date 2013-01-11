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

    public static final Object ENABLE_BEAN_LOOKUP = "beanBindings";
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

    /**
     * @return true if the value bound to the key
     *         {@value BeanLookupBindings#ENABLE_BEAN_LOOKUP} is either an
     *         instance of {@link java.lang.Boolean} or is a string that parses
     *         to {@code true} using {@link Boolean#parseBoolean(String)}.
     */
    public boolean isBeanLookupEnabled() {
        Object beanBindingsValue = super.get(ENABLE_BEAN_LOOKUP);
        return Boolean.TRUE.equals(beanBindingsValue)
                || (beanBindingsValue instanceof String
                    && Boolean.parseBoolean((String) beanBindingsValue));
    }
    
    @Override
    public Object get(Object key) {
        if (isBeanLookupEnabled() && key instanceof String) {
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
        if (isBeanLookupEnabled() && key instanceof String) {
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
        if (isBeanLookupEnabled() && appCtx.containsBean(name)) {
            throw new IllegalArgumentException("name conflict: \""+ name +"\" is the name of a bean.");
        }
        return super.put(name, value);
    }
}
