
package database;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Database {

    private final Map<String, String> storage = new HashMap<>();
    private final Map<String, Long> expiration = new HashMap<>();

    public static class SnapshotEntry {

        private final String value;
        private final long expirationTime;

        public SnapshotEntry(String value, long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }

        public String getValue() {
            return value;
        }

        public long getExpirationTime() {
            return expirationTime;
        }
    }

    private void removeExpiredKey(String key) {

        if (expiration.containsKey(key)) {
            if (System.currentTimeMillis() >= expiration.get(key)) {
                storage.remove(key);
                expiration.remove(key);
            }
        }
    }

    public synchronized void set(String key, String value) {
        storage.put(key, value);
        expiration.remove(key);
    }

    public synchronized String get(String key) {
        removeExpiredKey(key);
        return storage.get(key);
    }

    public synchronized boolean delete(String key) {
        removeExpiredKey(key);
        expiration.remove(key);
        return storage.remove(key) != null;
    }

    public synchronized boolean exists(String key) {
        removeExpiredKey(key);
        return storage.containsKey(key);
    }

    // Increment with overflow protection
    public synchronized long increment(String key) {

        removeExpiredKey(key);

        long value = 0L;

        if (storage.containsKey(key)) {
            value = Long.parseLong(storage.get(key));
        }

        long newValue = Math.addExact(value, 1L);

        storage.put(key, String.valueOf(newValue));

        return newValue;
    }

    // Decrement with underflow protection
    public synchronized long decrement(String key) {

        removeExpiredKey(key);

        long value = 0L;

        if (storage.containsKey(key)) {
            value = Long.parseLong(storage.get(key));
        }

        long newValue = Math.subtractExact(value, 1L);

        storage.put(key, String.valueOf(newValue));

        return newValue;
    }

    public synchronized boolean expire(String key, long seconds) {

        removeExpiredKey(key);

        if (!storage.containsKey(key)) {
            return false;
        }

        if (seconds <= 0) {
            delete(key);
            return true;
        }

        long milliseconds = Math.multiplyExact(seconds, 1000L);
        long expiryTime = Math.addExact(
                System.currentTimeMillis(),
                milliseconds
        );

        expiration.put(key, expiryTime);

        return true;
    }

    public synchronized boolean expireAt(
            String key, long expiryTimeMillis) {

        removeExpiredKey(key);

        if (!storage.containsKey(key)) {
            return false;
        }

        if (expiryTimeMillis <= System.currentTimeMillis()) {
            delete(key);
            return true;
        }

        expiration.put(key, expiryTimeMillis);

        return true;
    }

    public synchronized long getExpirationTime(String key) {

        removeExpiredKey(key);

        if (!storage.containsKey(key)) {
            return -1;
        }

        return expiration.getOrDefault(key, -1L);
    }

    public synchronized long ttl(String key) {

        removeExpiredKey(key);

        if (!storage.containsKey(key)) {
            return -2;
        }

        if (!expiration.containsKey(key)) {
            return -1;
        }

        long remaining =
                expiration.get(key) - System.currentTimeMillis();

        return (remaining + 999) / 1000;
    }

    public synchronized boolean persist(String key) {

        removeExpiredKey(key);

        if (!storage.containsKey(key)) {
            return false;
        }

        return expiration.remove(key) != null;
    }

    public synchronized Set<String> getAllKeys() {

        for (String key : new HashMap<>(storage).keySet()) {
            removeExpiredKey(key);
        }

        return new java.util.HashSet<>(storage.keySet());
    }

    public synchronized Map<String, SnapshotEntry> getSnapshot() {

        for (String key : new HashMap<>(storage).keySet()) {
            removeExpiredKey(key);
        }

        Map<String, SnapshotEntry> snapshot = new HashMap<>();

        for (String key : storage.keySet()) {

            String value = storage.get(key);

            long expiryTime =
                    expiration.getOrDefault(key, -1L);

            snapshot.put(
                    key,
                    new SnapshotEntry(value, expiryTime)
            );
        }

        return snapshot;
    }
}



