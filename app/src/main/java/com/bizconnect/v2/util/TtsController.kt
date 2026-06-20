package com.bizconnect.v2.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * 문자 읽어주기(TTS) 컨트롤러.
 *
 * 한국어 TextToSpeech 엔진을 감싸 화면 단위로 생성/해제한다.
 * - [speak]: 같은 컨트롤러로 재생 중이면 정지(토글), 아니면 새 문장 읽기.
 * - 엔진 준비(onInit) 전 호출은 무시되고, [pendingText]에 보관해 준비되면 자동 재생.
 */
class TtsController(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null

    /** 현재 읽어주는 중인지 — UI(아이콘 전환)에서 관찰. */
    var isSpeaking by mutableStateOf(false)
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.KOREAN)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                ready = true
                pendingText?.let { text ->
                    pendingText = null
                    speak(text)
                }
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { isSpeaking = true }
            override fun onDone(utteranceId: String?) { isSpeaking = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { isSpeaking = false }
            override fun onError(utteranceId: String?, errorCode: Int) { isSpeaking = false }
        })
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts ?: return
        if (!ready) { pendingText = text; return }
        if (isSpeaking) { stop(); return } // 토글: 재생 중 다시 누르면 정지
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "biz_msg")
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        isSpeaking = false
    }
}

/** 컴포지션 생명주기에 묶인 [TtsController]를 생성/해제한다. */
@Composable
fun rememberTtsController(): TtsController {
    val context = LocalContext.current
    val controller = remember { TtsController(context) }
    DisposableEffect(Unit) {
        onDispose { controller.shutdown() }
    }
    return controller
}
