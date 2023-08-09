package com.pierbezuhoff.dodeca.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * Usage:
 * ```
 * class X(..) : Y
 * , LifecycleInheritor by LifecycleInheritance()
 * ```
 * Then in client code:
 * ```
 * x.inheritLifecycleOf(...)
 * ```
 */
interface LifecycleInheritor : LifecycleOwner {
    val lifecycleInherited: Boolean
    fun inheritLifecycleOf(lifecycleOwner: LifecycleOwner)
}

/** Helper private object for lifecycle inheritance */
class LifecycleInheritance : LifecycleInheritor {
    private var inherited: Boolean = false
    override val lifecycleInherited: Boolean get() = inherited
    private var _lifecycle: Lifecycle = LifecycleRegistry(this)

//    override val lifecycle: Lifecycle = _lifecycle
    override fun getLifecycle(): Lifecycle =
        _lifecycle

    override fun inheritLifecycleOf(lifecycleOwner: LifecycleOwner) {
        require(!inherited)
        _lifecycle = lifecycleOwner.lifecycle
        inherited = true
    }
}
