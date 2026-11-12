package com.burhannexus.voicestudio.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.burhannexus.voicestudio.App
import com.burhannexus.voicestudio.model.ModelCatalog
import com.burhannexus.voicestudio.model.ModelInfo

@Composable
fun ModelsScreen(nav: NavController) {
    val app = App.instance
    val ctx = LocalContext.current
    val downloads by app.models.downloads.collectAsState()
    val revision by app.models.revision.collectAsState()
    var confirmRemove by remember { mutableStateOf<ModelInfo?>(null) }
    val ramMb = remember { app.models.deviceRamMb() }

    AppScreen(title = "AI Model Manager", onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BnCard {
                Text("Aap ka phone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LabelRow("RAM", "$ramMb MB")
                LabelRow("Khaali jagah", fmtBytes(app.models.freeBytes()))
                LabelRow("Models", fmtBytes(app.models.totalSize()) + "  (revision $revision)")
                Text(
                    "Offline models phone ke CPU par chalte hain: koi fees nahi, internet nahi. Raftar phone ke processor par munhasir hai - " +
                        "purane / kam RAM wale phone par bare models sust chalenge.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Text("Offline AI models (download)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ModelCatalog.models.forEach { m ->
                val installed = app.models.isInstalled(m)
                val dl = downloads[m.id]
                BnCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(m.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (installed) Badge("Installed", BnSecondary) else Badge(if (m.language == "ur") "Urdu" else "English", BnAccent)
                    }
                    Spacer(Modifier.height(6.dp))
                    LabelRow("Voices", m.speakers.joinToString(", ") { "${it.name} (${it.gender})" })
                    LabelRow("Download", "taqreeban ${m.downloadMb} MB" + if (installed) "  |  phone par ${fmtBytes(app.models.sizeOnDisk(m))}" else "")
                    LabelRow("RAM", "taqreeban ${m.ramMb} MB" + if (ramMb < m.ramMb * 3L) "  - aap ke phone ke liye bhaari ho sakta hai" else "")
                    LabelRow("Raftar", m.speed)
                    LabelRow("Emotions", "Nahi (neutral reading)")
                    LabelRow("License", m.license)
                    Spacer(Modifier.height(8.dp))
                    when {
                        dl != null && dl.phase != "error" -> {
                            Text((if (dl.phase == "downloading") "Download: " else "Install: ") + dl.message, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(progress = { dl.progress }, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { app.models.cancel(m) }) { Text("Cancel") }
                        }
                        installed -> OutlinedButton(onClick = { confirmRemove = m }) { Text("Remove") }
                        else -> {
                            if (dl != null) {
                                Text(dl.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(4.dp))
                            }
                            Button(onClick = { app.models.install(m) }) { Text(if (dl != null) "Dobara koshish" else "Download & Install") }
                        }
                    }
                }
            }

            Text("Phone System TTS (offline, pehle se maujood)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            BnCard {
                Text(
                    "Android ka apna TTS engine (aam taur par Speech Services by Google). Urdu aur English dono, pitch control ke sath, " +
                        "lekin awaz neural / cloud voices jitni qudrati nahi. Urdu ke liye neeche button se Urdu voice data install karein.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        try {
                            ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            toast(ctx, "Phone par TTS data installer nahi mila")
                        }
                    }) { Text("Voice data install") }
                    OutlinedButton(onClick = {
                        try {
                            ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            toast(ctx, "TTS settings nahi khul sakin")
                        }
                    }) { Text("TTS settings") }
                }
            }

            Text("Cloud model (online)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            BnCard {
                Text("Google Gemini TTS", fontWeight = FontWeight.SemiBold)
                LabelRow("Voices", "30 (Male + Female)")
                LabelRow("Zabanein", "Urdu, English aur mixed script")
                LabelRow("Emotions", "Haan - asli, model ke andar")
                LabelRow("Lagat", "Aap ki apni free API key; free tier ki limits Google tay karta hai aur badal sakti hain")
                LabelRow("Status", if (app.prefs.geminiKey.isBlank()) "API key set nahi" else "API key set hai")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { nav.navigate("settings") }) { Text("API Configuration") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    val target = confirmRemove
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Model remove karein?") },
            text = { Text("${target.title} phone se hata diya jayega. Is model ki voices wale projects dobara generate nahi ho sakenge jab tak model dobara install na ho.") },
            confirmButton = {
                TextButton(onClick = {
                    app.engines.sherpa.release()
                    app.models.remove(target)
                    confirmRemove = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}
