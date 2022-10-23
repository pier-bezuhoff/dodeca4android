package com.pierbezuhoff.dodeca.utils

/* python-style groupBy */
inline fun <E, K> Iterable<E>.consecutiveGroupBy(selector: (E) -> K): List<Pair<K, List<E>>> {
    val lists: MutableList<Pair<K, List<E>>> = mutableListOf()
    var k: K? = null
    var list: MutableList<E> = mutableListOf()
    for (e in this) {
        val newK = selector(e)
        if (k == newK) {
            list.add(e)
        } else {
            k?.let { lists.add(it to list) }
            k = newK
            list = mutableListOf(e)
        }
    }
    if (list.isNotEmpty())
        lists.add(k!! to list)
    return lists
}
