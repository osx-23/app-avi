package com.osx23.avi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osx23.avi.data.RegistroRepository
import com.osx23.avi.data.SupabaseProvider
import com.osx23.avi.model.Registro
import com.osx23.avi.service.FloatingBubbleService
import com.osx23.avi.ui.AviTheme
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AviTheme {
                MainScreen(
                    canDrawOverlays = { Settings.canDrawOverlays(this) },
                    hasMicPermission = {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    },
                    openOverlaySettings = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + packageName)
                            )
                        )
                    },
                    startBubble = {
                        ContextCompat.startForegroundService(
                            this,
                            Intent(this, FloatingBubbleService::class.java)
                        )
                    },
                    stopBubble = {
                        stopService(Intent(this, FloatingBubbleService::class.java))
                    }
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    canDrawOverlays: () -> Boolean,
    hasMicPermission: () -> Boolean,
    openOverlaySettings: () -> Unit,
    startBubble: () -> Unit,
    stopBubble: () -> Unit
) {
    val registros by RegistroRepository.registros.collectAsStateWithLifecycle()
    val bubbleRunning by FloatingBubbleService.isRunning.collectAsStateWithLifecycle()
    var permissionMessage by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { result ->
        val micOk = result[Manifest.permission.RECORD_AUDIO] ?: hasMicPermission()
        permissionMessage = if (micOk) {
            "Permiso de micrófono concedido. Pulsa ACTIVAR BURBUJA nuevamente."
        } else {
            "Debes conceder el permiso de micrófono."
        }
    }

    LaunchedEffect(Unit) {
        RegistroRepository.refresh()
    }

    Scaffold(containerColor = Color(0xFFF4F7FA)) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Registro Operativo",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF16324A)
                )
                Text("AVI · registro rápido por voz", color = Color(0xFF6B7F91))
            }

            item {
                StatusCard(
                    bubbleRunning = bubbleRunning,
                    supabaseConfigured = SupabaseProvider.isConfigured
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Burbuja flotante", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Actívala antes de abrir la aplicación operativa. " +
                                "La burbuja permanecerá encima para registrar fugas.",
                            color = Color(0xFF6B7F91),
                            fontSize = 14.sp
                        )

                        Button(
                            onClick = {
                                when {
                                    !hasMicPermission() -> {
                                        val permissions = buildList {
                                            add(Manifest.permission.RECORD_AUDIO)
                                            if (Build.VERSION.SDK_INT >= 33) {
                                                add(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }.toTypedArray()
                                        permissionLauncher.launch(permissions)
                                    }
                                    !canDrawOverlays() -> {
                                        permissionMessage =
                                            "Activa “Mostrar sobre otras aplicaciones” y vuelve a AVI."
                                        openOverlaySettings()
                                    }
                                    else -> {
                                        permissionMessage = ""
                                        startBubble()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ACTIVAR BURBUJA")
                        }

                        OutlinedButton(onClick = stopBubble, modifier = Modifier.fillMaxWidth()) {
                            Text("DESACTIVAR BURBUJA")
                        }

                        if (permissionMessage.isNotBlank()) {
                            Text(permissionMessage, fontSize = 13.sp, color = Color(0xFF587083))
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Últimos registros", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        registros.size.toString(),
                        color = Color(0xFF0B64D8),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (registros.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Text(
                            text = if (SupabaseProvider.isConfigured) {
                                "Aún no hay registros."
                            } else {
                                "Configura Supabase para guardar y consultar registros."
                            },
                            modifier = Modifier.padding(18.dp),
                            color = Color(0xFF6B7F91)
                        )
                    }
                }
            } else {
                items(registros, key = { it.id ?: (it.via.toString() + it.creadoEn) }) {
                    RegistroCard(it)
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun StatusCard(bubbleRunning: Boolean, supabaseConfigured: Boolean) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusLine("Burbuja", if (bubbleRunning) "ACTIVA" else "DESACTIVADA", bubbleRunning)
            StatusLine(
                "Supabase",
                if (supabaseConfigured) "CONFIGURADO" else "PENDIENTE",
                supabaseConfigured
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF6B7F91))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            color = if (ok) Color(0xFF087A55) else Color(0xFFC46A00)
        )
    }
}

@Composable
private fun RegistroCard(registro: Registro) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(registro.tipo, fontWeight = FontWeight.ExtraBold)
                Text(
                    registro.creadoEn.toDisplayTime(),
                    color = Color(0xFF6B7F91),
                    fontSize = 13.sp
                )
            }
            Text("Vía " + registro.via, fontSize = 16.sp)
            Text(
                registro.placa ?: "Sin placa",
                color = Color(0xFF0B64D8),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
    }
}

private fun String?.toDisplayTime(): String {
    if (this.isNullOrBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(this).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    }.getOrDefault(this)
}
