package com.foodrecommender.core

class MemoryPlaceCache(
    private val ttlMillis: Long = CACHE_TTL_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val entries = mutableMapOf<String, Entry>()

    fun get(id: String): List<Place>? {
        synchronized(lock) {
            val entry = entries[id] ?: return null
            if (now() - entry.storedAtMillis > ttlMillis) {
                entries.remove(id)
                return null
            }
            return entry.places
        }
    }

    fun put(id: String, places: List<Place>) {
        synchronized(lock) {
            entries[id] = Entry(places, now())
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }

    private data class Entry(val places: List<Place>, val storedAtMillis: Long)
}
