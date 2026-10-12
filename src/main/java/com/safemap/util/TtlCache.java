package com.safemap.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache em memória simples, com tempo de expiração e limite de tamanho.
 * Suficiente para uma única instância no Railway.
 */
public class TtlCache<K, V> {

    private record Entry<V>(V value, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxEntries;

    public TtlCache(Duration ttl, int maxEntries) {
        this.ttl = ttl;
        this.maxEntries = maxEntries;
    }

    public V get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) return null;
        if (!entry.isValid()) {
            entries.remove(key);
            return null;
        }
        return entry.value();
    }

    public void put(K key, V value) {
        if (entries.size() >= maxEntries) {
            entries.entrySet().removeIf(e -> !e.getValue().isValid());
            if (entries.size() >= maxEntries) {
                entries.clear();
            }
        }
        entries.put(key, new Entry<>(value, Instant.now().plus(ttl)));
    }
}
