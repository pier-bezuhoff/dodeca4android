package com.pierbezuhoff.dodeca.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

interface LifecycleInheritor : LifecycleOwner {
    val lifecycleInheritance: LifecycleInheritance
    val lifecycleInherited get() = lifecycleInheritance.inherited

    override fun getLifecycle(): Lifecycle =
        lifecycleInheritance.lifecycle

    fun inheritLifecycleOf(lifecycleOwner: LifecycleOwner) =
        lifecycleInheritance.inheritLifecycleOf(lifecycleOwner)
}

/** Helper inner object for lifecycle inheritance */
class LifecycleInheritance(target: LifecycleOwner) : LifecycleOwner {
    var inherited: Boolean = false
        private set
    private var _lifecycle: Lifecycle = LifecycleRegistry(target)

    override fun getLifecycle(): Lifecycle =
        _lifecycle

    /** Inherit lifecycle (of DduChooserActivity) */
    fun inheritLifecycleOf(lifecycleOwner: LifecycleOwner) {
        require(!inherited)
        _lifecycle = lifecycleOwner.lifecycle
        inherited = true
    }
}
