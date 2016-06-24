package de.caluga.morphium;

/**
 * Created by stephan on 18.09.15.
 */
public interface TypeMapper<T> {

    Object marshall(T o);

    T unmarshall(Object d);
}
