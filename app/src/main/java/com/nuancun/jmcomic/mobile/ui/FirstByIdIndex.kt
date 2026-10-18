package com.nuancun.jmcomic.mobile.ui

/** Preserves newest-first task selection when more than one task belongs to a work. */
internal class FirstByIdIndex<T>(items: List<T>, idOf: (T) -> String) {
    private val first = buildMap {
        items.forEach { item ->
            val id = idOf(item)
            if (!containsKey(id)) put(id, item)
        }
    }
    operator fun get(id: String): T? = first[id]
}
