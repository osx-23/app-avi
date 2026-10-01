package com.osx23.avi.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.osx23.avi.data.RegistroRepository
import com.osx23.avi.model.Registro
import com.osx23.avi.parser.VoiceCommandParser
import com.osx23.avi.speech.VoiceRecognizer
import com.osx23.avi.ui.AviTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

class FloatingBubbleService : Service() {

    companion object {
        val isRunning = MutableStateFlow(false)
        private const val CHANNEL_ID = "avi_overlay"
        private const val NOTIFICATION_ID = 151
    }

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var bubbleOwner: OverlayLifecycleOwner? = null
    private var panelOwner: OverlayLifecycleOwner? = null

    private val recognizedText = mutableStateOf("")
    private val viaText = mutableStateOf("")
    private val placaText = mutableStateOf("")
    private val statusText = mutableStateOf("")
    private val listening = mutableStateOf(false)

    private lateinit var voiceRecognizer: VoiceRecognizer

    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startAsForeground()

        voiceRecognizer = VoiceRecognizer(
            context = this,
            onText = ::handleVoiceText,
            onListeningChanged = { listening.value = it },
            onError = {
                statusText.value = it
                listening.value = false
            }
        )

        isRunning.value = true
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("AVI activo")
            .setContentText("Burbuja de registro operativo habilitada")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val type = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else -> 0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AVI - Registro operativo",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Mantiene activa la burbuja flotante de AVI"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun showBubble() {
        removePanel()
        if (bubbleView != null) return

        val size = dp(62)
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(16)
            y = dp(180)
        }

        val owner = OverlayLifecycleOwner()
        val view = ComposeView(this).apply {
            setOwners(owner)
            setContent { AviTheme { BubbleContent() } }
        }

        installDragAndClick(view, params)
        bubbleOwner = owner
        bubbleView = view
        windowManager.addView(view, params)
    }

    private fun showPanel() {
        removeBubble()
        if (panelView != null) return

        val width = min(resources.displayMetrics.widthPixels - dp(28), dp(390))
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val owner = OverlayLifecycleOwner()
        val view = ComposeView(this).apply {
            setOwners(owner)
            setContent {
                AviTheme {
                    PanelContent(
                        recognizedText = recognizedText,
                        viaText = viaText,
                        placaText = placaText,
                        statusText = statusText,
                        listening = listening,
                        onViaChange = { viaText.value = it.filter(Char::isDigit).take(4) },
                        onPlacaChange = {
                            placaText.value = it.filter(Char::isLetterOrDigit).uppercase().take(10)
                        },
                        onSpeak = ::startVoiceRecognition,
                        onMinimize = {
                            voiceRecognizer.stop()
                            showBubble()
                        },
                        onClear = ::clearForm,
                        onRegister = ::register
                    )
                }
            }
        }

        panelOwner = owner
        panelView = view
        windowManager.addView(view, params)
    }

    private fun startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            statusText.value = "Falta permiso de micrófono. Ábrelo desde la aplicación AVI."
            return
        }

        statusText.value = "Escuchando..."
        voiceRecognizer.start()
    }

    private fun handleVoiceText(text: String, isFinal: Boolean) {
        recognizedText.value = text
        val parsed = VoiceCommandParser.parse(text)
        parsed.via?.let { viaText.value = it.toString() }
        parsed.placa?.let { placaText.value = it }
        statusText.value = if (isFinal) "Revisa los datos antes de registrar." else "Escuchando..."
    }

    private fun register() {
        val via = viaText.value.toIntOrNull()
        if (via == null || via <= 0) {
            statusText.value = "Ingresa una vía válida."
            return
        }

        val registro = Registro(
            tipo = "FUGA",
            via = via,
            placa = placaText.value.ifBlank { null },
            textoReconocido = recognizedText.value.ifBlank { null }
        )

        serviceScope.launch {
            statusText.value = "Guardando en Supabase..."
            val result = RegistroRepository.registrar(registro)
            if (result.isSuccess) {
                statusText.value = "✓ Registro guardado"
                delay(900)
                clearForm()
                showBubble()
            } else {
                statusText.value = result.exceptionOrNull()?.message
                    ?: "No se pudo guardar el registro."
            }
        }
    }

    private fun clearForm() {
        recognizedText.value = ""
        viaText.value = ""
        placaText.value = ""
        statusText.value = ""
    }

    private fun installDragAndClick(view: View, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragged = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) dragged = true
                    params.x = (initialX + dx).coerceIn(
                        0,
                        resources.displayMetrics.widthPixels - params.width
                    )
                    params.y = (initialY + dy).coerceAtLeast(0)
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) showPanel()
                    true
                }
                else -> false
            }
        }
    }

    private fun removeBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        bubbleOwner?.destroy()
        bubbleOwner = null
    }

    private fun removePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        panelOwner?.destroy()
        panelOwner = null
    }

    override fun onDestroy() {
        if (::voiceRecognizer.isInitialized) voiceRecognizer.destroy()
        removePanel()
        removeBubble()
        serviceScope.cancel()
        isRunning.value = false
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun ComposeView.setOwners(owner: OverlayLifecycleOwner) {
        setViewTreeLifecycleOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
    }
}

@Composable
private fun BubbleContent() {
    Box(
        modifier = Modifier.size(62.dp).background(Color(0xFF0B64D8), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("🎤", fontSize = 25.sp)
    }
}

@Composable
private fun PanelContent(
    recognizedText: MutableState<String>,
    viaText: MutableState<String>,
    placaText: MutableState<String>,
    statusText: MutableState<String>,
    listening: MutableState<Boolean>,
    onViaChange: (String) -> Unit,
    onPlacaChange: (String) -> Unit,
    onSpeak: () -> Unit,
    onMinimize: () -> Unit,
    onClear: () -> Unit,
    onRegister: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("NUEVO REGISTRO", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("Registro rápido de fuga", color = Color(0xFF6B7F91), fontSize = 13.sp)
                }
                OutlinedButton(onClick = onMinimize) { Text("—") }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tipo", color = Color(0xFF587083))
                    Text("FUGA", fontWeight = FontWeight.Bold, color = Color(0xFF0B64D8))
                }
            }

            OutlinedTextField(
                value = viaText.value,
                onValueChange = onViaChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Vía") },
                singleLine = true
            )

            OutlinedTextField(
                value = placaText.value,
                onValueChange = onPlacaChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Placa") },
                singleLine = true
            )

            Button(
                onClick = onSpeak,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !listening.value
            ) {
                Text(if (listening.value) "🎤 ESCUCHANDO..." else "🎤 HABLAR")
            }

            if (recognizedText.value.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FA))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Texto reconocido", fontSize = 12.sp, color = Color(0xFF6B7F91))
                        Spacer(Modifier.height(4.dp))
                        Text(recognizedText.value, fontSize = 14.sp)
                    }
                }
            }

            if (statusText.value.isNotBlank()) {
                Text(
                    text = statusText.value,
                    fontSize = 13.sp,
                    color = if (statusText.value.startsWith("✓")) {
                        Color(0xFF087A55)
                    } else {
                        Color(0xFF4D6475)
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text("LIMPIAR")
                }
                Button(
                    onClick = onRegister,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B64D8))
                ) {
                    Text("REGISTRAR")
                }
            }
        }
    }
}

private class OverlayLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
