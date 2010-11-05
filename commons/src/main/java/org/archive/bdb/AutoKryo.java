package org.archive.bdb;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import sun.reflect.ReflectionFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.SerializationException;

/**
 * Extensions to Kryo to let classes control their own registration, suggest
 * other classes to register together, and use the same (Sun-JVM-only) trick
 * for deserializing classes without no-arg constructors.
 * 
 * TODO: more comments!
 * 
 * @contributor gojomo
 */
@SuppressWarnings("unchecked")
public class AutoKryo extends Kryo {
    ArrayList<Class> registeredClasses = new ArrayList<Class>(); 
    
    @Override
    protected void handleUnregisteredClass(Class type) {
        System.err.println("UNREGISTERED FOR KRYO "+type+" in "+registeredClasses.get(0));
        super.handleUnregisteredClass(type);
    }

    public void autoregister(Class type) {
        if (registeredClasses.contains(type)) {
            return;
        }
        registeredClasses.add(type); 
        try {
            invokeStatic(
                "autoregisterTo", 
                type,
                new Class[]{ ((Class)AutoKryo.class), }, 
                new Object[] { this, });
        } catch (Exception e) {
            register(type); 
        }
    }

    
    @Override
    public <T> T newInstance(Class<T> type) {
        SerializationException ex = null; 
        try {
            return super.newInstance(type);
        } catch (SerializationException se) {
            ex = se;
        }
        try {
            final Constructor<?> constructor = 
            ReflectionFactory.getReflectionFactory().newConstructorForSerialization( 
                    type, 
                    Object.class.getDeclaredConstructor( new Class[0] ) );
            constructor.setAccessible( true );
            Object inst = constructor.newInstance( new Object[0] );
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

    protected Object invokeStatic(String method, Class clazz, Class[] types, Object[] args) throws Exception {
        return clazz.getMethod(method, types).invoke(null, args);
    }
}
