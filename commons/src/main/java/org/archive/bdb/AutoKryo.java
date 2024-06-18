package org.archive.bdb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import com.esotericsoftware.kryo.util.MapReferenceResolver;
import org.objenesis.strategy.SerializingInstantiatorStrategy;

/**
 * Extensions to Kryo to let classes control their own registration, suggest
 * other classes to register together, and use SerializingInstantiatorStrategy
 * for deserializing classes without no-arg constructors.
 *
 * @author gojomo
 */
@SuppressWarnings("unchecked")
public class AutoKryo extends Kryo {
    protected ArrayList<Class<?>> registeredClasses = new ArrayList<>();
    protected Set<Class<?>> referenceClasses = new HashSet<>();

    public AutoKryo() {
        super();
        setInstantiatorStrategy(new DefaultInstantiatorStrategy(new SerializingInstantiatorStrategy()));
    }

    public void autoregister(Class<?> type) {
        if (registeredClasses.contains(type)) {
            return;
        }
        registeredClasses.add(type);
        try {
            type.getMethod("autoregisterTo", new Class[]{AutoKryo.class}).invoke(null, this);
        } catch (Exception e) {
            register(type); 
        }
    }

    public void useReferencesFor(Class<?> clazz) {
        if (!getReferences()) {
            setReferenceResolver(new MapReferenceResolver() {
                @Override
                public boolean useReferences(Class type) {
                    return referenceClasses.contains(type);
                }
            });
        }
        referenceClasses.add(clazz);
    }
}
