package com.bizconnect.v2.data.sync

import android.database.ContentObserver
import android.os.Handler
import java.util.concurrent.atomic.AtomicBoolean

class SmsContentObserver(
    handler: Handler,
    private val onSmsChanged: () -> Unit
) : ContentObserver(handler) {

    private val pending = AtomicBoolean(false)
    private val debounceHandler = handler

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        if (pending.compareAndSet(false, true)) {
            debounceHandler.postDelayed({
                pending.set(false)
                onSmsChanged()
            }, DEBOUNCE_MS)
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
    }
}
