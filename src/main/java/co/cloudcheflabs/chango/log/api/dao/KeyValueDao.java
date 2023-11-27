package co.cloudcheflabs.chango.log.api.dao;

public interface KeyValueDao<K, V> {
    void save(K key, V value);
    V find(K key, Class<V> clazz);
    void delete(K key);
}
