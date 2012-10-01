package org.archive.bdb;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import sun.reflect.ReflectionFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.SerializationException;

/**
 * Extensions to Kryo to let classes control their own registration, suggest
 * other classes to register together, and use the same (Sun-JVM-only) trick
 * for deserializing classes without no-arg constructors.
 * 
 * newInstance technique and constructor caching inspired by the 
 * KryoReflectionFactorySupport class of Martin Grotzke's kryo-serializers 
 * project. <https://github.com/magro/kryo-serializers>
 * 
 * TODO: more comments!
 * 
 * @contributor gojomo
 */
@SuppressWarnings("unchecked")
public class AutoKryo extends Kryo {
    protected ArrayList<Class<?>> registeredClasses = new ArrayList<Class<?>>(); 
    
    @Override
    protected void handleUnregisteredClass(@SuppressWarnings("rawtypes") Class type) {
        System.err.println("UNREGISTERED FOR KRYO "+type+" in "+registeredClasses.get(0));
        super.handleUnregisteredClass(type);
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

    protected static final ReflectionFactory REFLECTION_FACTORY = ReflectionFactory.getReflectionFactory();
    protected static final Object[] INITARGS = new Object[0];
    protected static final Map<Class<?>, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<Class<?>, Constructor<?>>();
    
    @Override
    public <T> T newInstance(Class<T> type) {
        SerializationException ex = null; 
        try {
            return super.newInstance(type);
        } catch (SerializationException se) {
            ex = se;
        }
        try {
            Constructor<?> constructor = CONSTRUCTOR_CACHE.get(type);
            if(constructor == null) {
                constructor = REFLECTION_FACTORY.newConstructorForSerialization( 
                        type, Object.class.getDeclaredConstructor( new Class[0] ) );
                constructor.setAccessible( true );
                CONSTRUCTOR_CACHE.put(type, constructor);
            }
            Object inst = constructor.newInstance( INITARGS );
            return (T) inst;
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
             e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        throw ex;
    }

    protected Object invokeStatic(String method, Class<?> clazz, Class<?>[] types, Object[] args) throws Exception {
        return clazz.getMethod(method, types).invoke(null, args);
    }
}
