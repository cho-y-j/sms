package com.bizconnect.v2.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CallDetectorTest {

    private lateinit var callDetector: CallDetector

    @Before
    fun setup() {
        callDetector = CallDetector()
    }

    @Test
    fun detectIncomingAnsweredCallEnded() {
        callDetector.onCallStateChanged(
            number = "01012345678",
            state = CallState.INCOMING
        )
        callDetector.onCallStateChanged(
            number = "01012345678",
            state = CallState.ACTIVE
        )
        val result = callDetector.onCallStateChanged(
            number = "01012345678",
            state = CallState.IDLE
        )

        assertEquals(CallEvent.IncomingCallEnded("01012345678"), result)
    }

    @Test
    fun detectMissedIncomingCall() {
        callDetector.onCallStateChanged(
            number = "01087654321",
            state = CallState.INCOMING
        )
        val result = callDetector.onCallStateChanged(
            number = "01087654321",
            state = CallState.IDLE
        )

        assertEquals(CallEvent.MissedCall("01087654321"), result)
    }

    @Test
    fun detectOutgoingCallEnded() {
        callDetector.onCallStateChanged(
            number = "01055556666",
            state = CallState.OUTGOING
        )
        callDetector.onCallStateChanged(
            number = "01055556666",
            state = CallState.ACTIVE
        )
        val result = callDetector.onCallStateChanged(
            number = "01055556666",
            state = CallState.IDLE
        )

        assertEquals(CallEvent.OutgoingCallEnded("01055556666"), result)
    }

    @Test
    fun detectBusyRejectedWhileInCall() {
        callDetector.onCallStateChanged(
            number = "01012345678",
            state = CallState.ACTIVE
        )
        val result = callDetector.onCallStateChanged(
            number = "01099999999",
            state = CallState.IDLE
        )

        assertEquals(CallEvent.CallRejected("01099999999"), result)
    }

    @Test
    fun handleRapidStateChanges() {
        val numbers = listOf("01011111111", "01022222222", "01033333333")
        var lastEvent: CallEvent? = null

        for (number in numbers) {
            callDetector.onCallStateChanged(number, CallState.INCOMING)
            lastEvent = callDetector.onCallStateChanged(number, CallState.IDLE)
        }

        assertEquals(CallEvent.MissedCall("01033333333"), lastEvent)
    }

    @Test
    fun resetStateMachine() {
        callDetector.onCallStateChanged(
            number = "01012345678",
            state = CallState.INCOMING
        )

        callDetector.reset()

        val result = callDetector.getCurrentState()
        assertEquals(CallState.IDLE, result)
        assertEquals(null, callDetector.getCurrentNumber())
    }

    @Test
    fun handleNullPhoneNumber() {
        val result = callDetector.onCallStateChanged(
            number = null,
            state = CallState.INCOMING
        )

        assertNull(result)
    }

    @Test
    fun handleConcurrentCalls() {
        callDetector.onCallStateChanged(
            number = "01012345678",
            state = CallState.ACTIVE
        )

        val result = callDetector.onCallStateChanged(
            number = "01087654321",
            state = CallState.INCOMING
        )

        assertEquals(CallEvent.IncomingCallDuringActive("01087654321"), result)
    }
}

enum class CallState {
    IDLE,
    INCOMING,
    OUTGOING,
    ACTIVE,
    HOLD
}

sealed class CallEvent {
    data class IncomingCallEnded(val number: String) : CallEvent()
    data class OutgoingCallEnded(val number: String) : CallEvent()
    data class MissedCall(val number: String) : CallEvent()
    data class CallRejected(val number: String) : CallEvent()
    data class IncomingCallDuringActive(val number: String) : CallEvent()
}

class CallDetector {
    private var previousState = CallState.IDLE
    private var currentState = CallState.IDLE
    private var currentNumber: String? = null
    private var previousNumber: String? = null

    fun onCallStateChanged(number: String?, state: CallState): CallEvent? {
        if (number == null) {
            return null
        }

        previousNumber = currentNumber
        previousState = currentState
        currentNumber = number
        currentState = state

        return detectCallEvent()
    }

    private fun detectCallEvent(): CallEvent? {
        return when {
            // Incoming call that was answered and now ended
            previousState == CallState.ACTIVE && currentState == CallState.IDLE &&
                    previousNumber != null -> CallEvent.IncomingCallEnded(previousNumber!!)

            // Outgoing call that was active and now ended
            previousState == CallState.ACTIVE && currentState == CallState.IDLE &&
                    currentNumber != null -> CallEvent.OutgoingCallEnded(currentNumber!!)

            // Incoming call that was never answered (went straight to IDLE)
            previousState == CallState.INCOMING && currentState == CallState.IDLE ->
                CallEvent.MissedCall(currentNumber!!)

            // Call rejected while device was in active call
            previousState == CallState.ACTIVE && currentState == CallState.IDLE &&
                    currentNumber != previousNumber -> CallEvent.CallRejected(currentNumber!!)

            // Incoming call while already on a call
            currentState == CallState.INCOMING && previousState == CallState.ACTIVE ->
                CallEvent.IncomingCallDuringActive(currentNumber!!)

            else -> null
        }
    }

    fun getCurrentState(): CallState = currentState

    fun getCurrentNumber(): String? = currentNumber

    fun reset() {
        previousState = CallState.IDLE
        currentState = CallState.IDLE
        currentNumber = null
        previousNumber = null
    }
}
