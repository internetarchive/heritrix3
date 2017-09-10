package org.archive.bdb;

import java.util.ArrayList;

import org.objenesis.strategy.StdInstantiatorStrategy;

import com.esotericsoftware.kryo.Kryo;

/**
 * Extensions to Kryo to let classes control their own registration, suggest
 * other classes to register together, and use the same (Sun-JVM-only) trick for
 * deserializing classes without no-arg constructors.
 * 
 * newInstance technique and constructor caching inspired by the
 * KryoReflectionFactorySupport class of Martin Grotzke's kryo-serializers
 * project. <https://github.com/magro/kryo-serializers>
 * 
 * anjackson: Took the opportunity to cut down complexity here while upgrading
 * Kryo to version 3.
 * 
 * TODO: more comments!
 * 
 * @contributor gojomo, anjackson
 */
@SuppressWarnings("unchecked")
public class AutoKryo extends Kryo {
    protected ArrayList<Class<?>> registeredClasses = new ArrayList<Class<?>>(); 
    
    public AutoKryo() {
        // Avoid necessity of a no-arg constructor, falling back on a
        // different method if non is present:
        setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(
                new StdInstantiatorStrategy()));
    }

    public void autoregister(Class<?> type) {
        if (registeredClasses.contains(type)) {
            return;
        }
        registeredClasses.add(type); 
        try {
            invokeStatic(
                "autoregisterTo", 
                type,
                new Class[]{ ((Class<?>)AutoKryo.class), }, 
                new Object[] { this, });
        } catch (Exception e) {
            register(type); 
        }
    }

    protected Object invokeStatic(String method, Class<?> clazz, Class<?>[] types, Object[] args) throws Exception {
        return clazz.getMethod(method, types).invoke(null, args);
    }
}
