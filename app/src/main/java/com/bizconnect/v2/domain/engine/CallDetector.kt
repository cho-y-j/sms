package com.bizconnect.v2.domain.engine

import android.telephony.TelephonyManager
import javax.inject.Inject

/**
 * Stateful call detector that tracks phone state transitions
 * to accurately determine call events.
 *
 * State machine:
 * IDLE → RINGING → OFFHOOK → IDLE = incoming answered then ended
 * IDLE → RINGING → IDLE = incoming missed
 * IDLE → OFFHOOK → IDLE = outgoing ended
 * RINGING (during OFFHOOK) → IDLE = busy/rejected
 */
class CallDetector @Inject constructor() {

    data class CallEvent(
        val type: CallbackEventType,
        val phoneNumber: String,
        val duration: Long,  // milliseconds
        val timestamp: Long
    )

    data class CallStateInfo(
        val state: Int,
        val isInCall: Boolean,
        val incomingNumber: String?,
        val callStartTime: Long
    )

    // State tracking variables
    private var previousState = TelephonyManager.CALL_STATE_IDLE
    private var currentState = TelephonyManager.CALL_STATE_IDLE
    private var incomingNumber: String? = null
    private var outgoingNumber: String? = null
    private var callStartTime: Long = 0L
    private var isIncomingCall = false
    private var wasCallAnswered = false
    private var wasInCallWhenRinging = false

    /**
     * Process new phone state and return event if call ended
     */
    fun onStateChanged(state: Int, phoneNumber: String?): CallEvent? {
        previousState = currentState
        currentState = state

        return when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                handleRingingState(phoneNumber)
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                handleOffhookState(phoneNumber)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                handleIdleState()
            }
            else -> null
        }
    }

    /**
     * Handle RINGING state - incoming call detected
     */
    private fun handleRingingState(phoneNumber: String?): CallEvent? {
        // RINGING state means incoming call is arriving
        incomingNumber = phoneNumber
        isIncomingCall = true
        wasCallAnswered = false
        wasInCallWhenRinging = false

        // Check if user was already in a call when this ringing started
        if (previousState == TelephonyManager.CALL_STATE_OFFHOOK) {
            wasInCallWhenRinging = true
        }

        return null // No event on ringing, wait for answer or rejection
    }

    /**
     * Handle OFFHOOK state - call is active
     */
    private fun handleOffhookState(phoneNumber: String?): CallEvent? {
        // OFFHOOK means call is active (either incoming answered or outgoing)
        when {
            previousState == TelephonyManager.CALL_STATE_RINGING -> {
                // Incoming call was answered
                wasCallAnswered = true
                callStartTime = System.currentTimeMillis()
                return null // Wait for IDLE to log the event
            }
            previousState == TelephonyManager.CALL_STATE_IDLE -> {
                // Outgoing call started
                isIncomingCall = false
                wasCallAnswered = true
                outgoingNumber = phoneNumber
                callStartTime = System.currentTimeMillis()
                return null // Wait for IDLE to log the event
            }
            else -> {
                // Call state changed within OFFHOOK (unlikely but possible)
                return null
            }
        }
    }

    /**
     * Handle IDLE state - call ended
     */
    private fun handleIdleState(): CallEvent? {
        // IDLE means call ended
        val event = when {
            // Missed incoming call: RINGING -> IDLE (without OFFHOOK)
            previousState == TelephonyManager.CALL_STATE_RINGING && !wasCallAnswered -> {
                incomingNumber?.let { number ->
                    CallEvent(
                        type = CallbackEventType.MISSED,
                        phoneNumber = number,
                        duration = 0L,
                        timestamp = System.currentTimeMillis()
                    )
                }
            }

            // Call was busy: OFFHOOK (with prior incoming) -> RINGING -> IDLE
            // This happens when user rejects while on another call
            previousState == TelephonyManager.CALL_STATE_RINGING &&
                    wasInCallWhenRinging && !wasCallAnswered -> {
                incomingNumber?.let { number ->
                    CallEvent(
                        type = CallbackEventType.BUSY,
                        phoneNumber = number,
                        duration = 0L,
                        timestamp = System.currentTimeMillis()
                    )
                }
            }

            // Normal call ended: OFFHOOK -> IDLE
            previousState == TelephonyManager.CALL_STATE_OFFHOOK && wasCallAnswered -> {
                val duration = System.currentTimeMillis() - callStartTime
                val number = if (isIncomingCall) {
                    incomingNumber
                } else {
                    outgoingNumber
                }

                number?.let { phoneNum ->
                    CallEvent(
                        type = CallbackEventType.ENDED,
                        phoneNumber = phoneNum,
                        duration = duration,
                        timestamp = System.currentTimeMillis()
                    )
                }
            }

            else -> null
        }

        // Reset state for next call
        if (event != null) {
            reset()
        }

        return event
    }

    /**
     * Reset state machine
     */
    fun reset() {
        previousState = TelephonyManager.CALL_STATE_IDLE
        currentState = TelephonyManager.CALL_STATE_IDLE
        incomingNumber = null
        outgoingNumber = null
        callStartTime = 0L
        isIncomingCall = false
        wasCallAnswered = false
        wasInCallWhenRinging = false
    }

    /**
     * Get current call state info
     */
    fun getCurrentState(): CallStateInfo {
        return CallStateInfo(
            state = currentState,
            isInCall = currentState == TelephonyManager.CALL_STATE_OFFHOOK,
            incomingNumber = incomingNumber,
            callStartTime = callStartTime
        )
    }
}
