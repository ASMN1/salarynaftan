package com.example.salarynaftan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Scope коротких операций, которым нужно пережить уничтожение ViewModel,
 * но не должно быть неуправляемого GlobalScope в доменном коде.
 */
object AppCoroutineScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.IO
}