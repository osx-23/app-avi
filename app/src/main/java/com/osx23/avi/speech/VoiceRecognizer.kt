package com.osx23.avi.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceRecognizer(
    context: Context,
    private val onText: (String, Boolean) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("El reconocimiento de voz no está disponible en este dispositivo.")
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(listener)
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        onListeningChanged(true)
        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        onListeningChanged(false)
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        onListeningChanged(false)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() { onListeningChanged(false) }
        override fun onError(error: Int) {
            onListeningChanged(false)
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Error de audio."
                SpeechRecognizer.ERROR_CLIENT -> "Reconocimiento cancelado."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Error de red."
                SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió la frase."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocedor está ocupado."
                SpeechRecognizer.ERROR_SERVER -> "Error del servicio de reconocimiento."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz."
                else -> "Error de reconocimiento (" + error + ")."
            }
            onError(message)
        }
        override fun onResults(results: Bundle?) {
            onListeningChanged(false)
            results.bestText()?.let { onText(it, true) }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults.bestText()?.let { onText(it, false) }
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun Bundle.bestText(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
}
