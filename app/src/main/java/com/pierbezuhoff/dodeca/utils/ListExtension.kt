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

inline fun <E> Iterable<E>.filteredIndices(selector: (E) -> Boolean): List<Int> =
    withIndex().filter { (_, e) -> selector(e) }.map { (i, _) -> i }

class LexicographicComparator<T : Comparable<T>> : Comparator<List<T>> {
    override fun compare(list1: List<T>, list2: List<T>): Int {
        for (i in 0 until minOf(list1.size, list2.size)) {
            val compareElements = list1[i].compareTo(list2[i])
            if (compareElements != 0)
                return compareElements
        }
        return list1.size.compareTo(list2.size)
    }
}