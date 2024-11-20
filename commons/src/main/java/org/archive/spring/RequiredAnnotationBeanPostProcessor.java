package org.archive.spring;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Method;

public class RequiredAnnotationBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeanInitializationException {
        for (Method method : bean.getClass().getMethods()) {
            if (method.isAnnotationPresent(Required.class)) {
                Method getter;

                if (method.getName().startsWith("set")) {
                    String getterName = "get" + method.getName().substring(3);
                    try {
                        getter = bean.getClass().getMethod(getterName);
                    } catch (NoSuchMethodException e) {
                        throw new BeanInitializationException("Missing getter for @Required property on bean: " +
                                                              beanName + " for method: " + method.getName());
                    }
                } else {
                    getter = method;
                }

                try {
                    Object value = getter.invoke(bean);
                    if (value == null) {
                        throw new BeanInitializationException("Property not set for bean: " + beanName +
                                                              " on method: " + method.getName());
                    }
                } catch (Exception e) {
                    throw new BeanInitializationException("Error processing @Required for bean: " + beanName, e) {};
                }
            }
        }
        return bean;
    }
}
