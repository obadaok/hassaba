package com.platinum.vip.hasiba

import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VoiceInputHelper(
    private val activity: Activity,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStart: () -> Unit,
    private val onListeningStop: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var originalAudioVolume = -1
    private val audioManager = activity.getSystemService(Activity.AUDIO_SERVICE) as AudioManager
    private var timeoutJob: Job? = null
    private var lastErrorTime = 0L
    private var isRecreating = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val VOICE_PERMISSION_CODE = 456

    private val ignoredWords = setOf(
        "من فضلك", "أهلاً", "مرحباً", "شكراً", "لو سمحت", "بليز", "please",
        "حبيبي", "يا", "أستاذ", "باشا", "أخي", "عمو", "خالة", "ممكن", "عايز", "عاوز"
    )

    init {
        recreateRecognizer()
    }

    private fun recreateRecognizer() {
        if (isRecreating) return
        isRecreating = true
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        if (speechRecognizer == null) {
            onError("التعرف الصوتي غير متوفر على هذا الجهاز")
            return
        }
        speechRecognizer!!.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                onListeningStart()
                startTimeoutTimer()
            }

            override fun onBeginningOfSpeech() {
                cancelTimeoutTimer()
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                cancelTimeoutTimer()
            }

            override fun onError(error: Int) {
                isListening = false
                cancelTimeoutTimer()
                onListeningStop()

                val now = System.currentTimeMillis()
                if (now - lastErrorTime < 1000) return
                lastErrorTime = now

                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "خطأ في الصوت، تأكد من الميكروفون"
                    SpeechRecognizer.ERROR_CLIENT -> "خطأ في التطبيق، حاول مرة أخرى"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "لا توجد صلاحية للميكروفون"
                    SpeechRecognizer.ERROR_NETWORK -> "تأكد من اتصالك بالإنترنت"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "انتهت مهلة الشبكة"
                    SpeechRecognizer.ERROR_NO_MATCH -> "لم أسمع بوضوح، أعد المحاولة"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "المتعرف الصوتي مشغول"
                    SpeechRecognizer.ERROR_SERVER -> "خطأ في الخادم"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم يتم سماع أي كلام"
                    else -> "حدث خطأ غير معروف"
                }
                onError(errorMessage)
                restoreAudioVolume()
                mainHandler.post { if (!isRecreating) recreateRecognizer() }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                cancelTimeoutTimer()
                onListeningStop()

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    var spokenText = matches[0]

                    ignoredWords.forEach { word ->
                        spokenText = spokenText.replace(word, "", ignoreCase = true)
                    }

                    val mathExpression = MathDictionary.convertToMathExpression(spokenText)
                    if (mathExpression.isNotEmpty()) {
                        onResult(mathExpression)
                    } else {
                        onError("لم يتم التعرف على عملية حسابية صحيحة")
                    }
                } else {
                    onError("لم يتم التعرف على أي نص")
                }
                restoreAudioVolume()
                mainHandler.post { if (!isRecreating) recreateRecognizer() }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        isRecreating = false
    }

    private fun startTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(20000)
            if (isListening) {
                stopListening()
                onError("انتهت مهلة الاستماع، يرجى التحدث بشكل أسرع")
            }
        }
    }

    private fun cancelTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    fun startListening() {
        if (isListening) {
            stopListening()
            return
        }
        if (!checkVoicePermission()) {
            requestVoicePermission()
            return
        }
        if (speechRecognizer == null) {
            recreateRecognizer()
            if (speechRecognizer == null) {
                onError("التعرف الصوتي غير متوفر على هذا الجهاز")
                return
            }
        }
        muteSystemSound()

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث بالعملية الحسابية...")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer!!.startListening(intent)
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            cancelTimeoutTimer()
            restoreAudioVolume()
            onListeningStop()
        }
        isListening = false
    }

    fun isListening(): Boolean = isListening

    fun destroy() {
        stopListening()
        timeoutJob?.cancel()
        scope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun muteSystemSound() {
        try {
            originalAudioVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun restoreAudioVolume() {
        if (originalAudioVolume != -1) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalAudioVolume, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            originalAudioVolume = -1
        }
    }

    fun checkVoicePermission(): Boolean {
        return ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun requestVoicePermission() {
        ActivityCompat.requestPermissions(activity, arrayOf(android.Manifest.permission.RECORD_AUDIO), VOICE_PERMISSION_CODE)
    }
}