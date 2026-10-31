package com.caxture.manhuntuniversal.api.config;
/** Main-thread persistence delegate. A false/throwing save must leave storage unchanged. */
public interface ConfigStorage<T> {
    T read();
    boolean save(T value);
}
