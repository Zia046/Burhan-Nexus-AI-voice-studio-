package com.burhannexus.voicestudio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.burhannexus.voicestudio.App
import com.burhannexus.voicestudio.model.ModelCatalog
import com.burhannexus.voicestudio.tts.SynthRequest
import com.burhannexus.voicestudio.tts.Voice
import kotlinx.coroutines.launch
import java.io.File

private const val SAMPLE_UR = "السلام علیکم، یہ برہان نیکسس اے آئی وائس اسٹوڈیو کی آواز کا نمونہ ہے۔"
private const val SAMPLE_EN = "Hello, this is a voice preview from Burhan Nexus A I Voice Studio."

@Composable
fun VoicePickerDialog(voices: List<Voice>, selected: String, onDismiss: () -> Unit, onPick: (Voice) -> Unit) {
    val app = App.instance
    var mode by remember { mutableStateOf("all") }
    val shown = voices.filter {
        val online = app.engines.engine(it.engineId)?.online == true
        mode == "all" || (mode == "online") == online
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voice chunein") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == "all", onClick = { mode = "all" }, label = { Text("All") })
                    FilterChip(selected = mode == "offline", onClick = { mode = "offline" }, label = { Text("Offline") })
                    FilterChip(selected = mode == "online", onClick = { mode = "online" }, label = { Text("Online") })
                }
                if (shown.isEmpty()) {
                    Text("Is mode mein koi voice nahi. Offline ke liye Model Manager se model install karein.", modifier = Modifier.padding(top = 12.dp))
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(shown, key = { it.id }) { v ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(v) }.padding(vertical = 8.dp)) {
                            Text(v.name, fontWeight = if (v.id == selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (v.id == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(
                                "${v.gender}  |  ${v.language.uppercase()}  |  ${app.engines.engine(v.engineId)?.name ?: ""}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Band karein") } },
    )
}

@Composable
fun VoiceLibraryScreen(nav: NavController) {
    val app = App.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by app.models.revision.collectAsState()
    val voices by produceState(initialValue = emptyList<Voice>(), revision) { value = app.engines.allVoices() }
    val playing by app.player.playing.collectAsState()

    var gender by remember { mutableStateOf("All") }
    var language by remember { mutableStateOf("All") }
    var favOnly by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(app.prefs.favorites) }
    var defaultVoice by remember { mutableStateOf(app.prefs.defaultVoiceId) }
    var previewing by remember { mutableStateOf("") }
    var consentVoice by remember { mutableStateOf<Voice?>(null) }

    fun previewFile(v: Voice) = File(ctx.cacheDir, "preview_${v.id.hashCode()}.wav")

    fun preview(v: Voice) {
        val file = previewFile(v)
        if (playing == file.path) {
            app.player.stop()
            return
        }
        if (file.exists() && file.length() > 44) {
            app.player.play(file)
            return
        }
        if (previewing.isNotEmpty()) return
        scope.launch {
            previewing = v.id
            try {
                val sample = if (v.language == "en") SAMPLE_EN else SAMPLE_UR
                // The preview is synthesised right now by the real engine - never a pre-recorded demo.
                app.engines.engineOf(v).synthesize(SynthRequest(sample, v, 1f, 1f, null, 50), file)
                app.player.play(file)
            } catch (e: Throwable) {
                file.delete()
                toast(ctx, "Preview nakaam: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                previewing = ""
            }
        }
    }

    val shown = voices.filter { v ->
        (gender == "All" || v.gender == gender) &&
            (language == "All" || (language == "Urdu" && v.speaks("ur")) || (language == "English" && v.speaks("en"))) &&
            (!favOnly || v.id in favorites)
    }
    val notInstalled = ModelCatalog.models.filter { !app.models.isInstalled(it) }

    AppScreen(title = "AI Voice Library", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Male", "Female").forEach { g ->
                        FilterChip(selected = gender == g, onClick = { gender = g }, label = { Text(g) })
                    }
                    listOf("Urdu", "English").forEach { l ->
                        FilterChip(selected = language == l, onClick = { language = if (language == l) "All" else l }, label = { Text(l) })
                    }
                    FilterChip(selected = favOnly, onClick = { favOnly = !favOnly }, label = { Text("Favorites") })
                }
                Text(
                    "${shown.size} voices dastiyab. Preview hamesha asli model se usi waqt banta hai.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(shown, key = { it.id }) { v ->
                val engine = app.engines.engine(v.engineId)
                BnCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.name, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Badge(v.gender, if (v.gender == "Female") BnSecondary else if (v.gender == "Male") BnAccent else MaterialTheme.colorScheme.surfaceVariant)
                                Badge(if (engine?.online == true) "Online" else "Offline", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                if (v.id == defaultVoice) Badge("Default", BnPrimary, androidx.compose.ui.graphics.Color.White)
                            }
                        }
                        IconButton(onClick = {
                            favorites = if (v.id in favorites) favorites - v.id else favorites + v.id
                            app.prefs.favorites = favorites
                        }) {
                            Icon(
                                if (v.id in favorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorite", tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LabelRow("Language", when (v.language) { "ur" -> "Urdu"; "en" -> "English"; else -> "Urdu + English (multilingual)" })
                    LabelRow("Accent", v.accent)
                    LabelRow("Engine", engine?.name ?: "")
                    LabelRow("Emotions", if (engine?.supportsEmotion == true) "Haan - model khud support karta hai" else "Nahi - sirf neutral reading")
                    LabelRow("Tafseel", v.description)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                if (engine?.online == true && !app.prefs.cloudConsent) consentVoice = v else preview(v)
                            },
                            enabled = previewing.isEmpty() || previewing == v.id,
                        ) {
                            if (previewing == v.id) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(if (playing == previewFile(v).path) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Preview")
                        }
                        OutlinedButton(onClick = {
                            app.prefs.defaultVoiceId = v.id
                            defaultVoice = v.id
                            toast(ctx, "Default voice set ho gayi")
                        }) { Text("Default banayein") }
                    }
                }
            }
            if (notInstalled.isNotEmpty()) {
                item {
                    Text("Download ke liye dastiyab offline voices", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
                items(notInstalled, key = { "dl-" + it.id }) { m ->
                    BnCard {
                        Text(m.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${m.speakers.size} voice(s): " + m.speakers.joinToString(", ") { "${it.name} (${it.gender})" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("Taqreeban ${m.downloadMb} MB download", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { nav.navigate("models") }) { Text("Download / Install") }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    val cv = consentVoice
    if (cv != null) {
        AlertDialog(
            onDismissRequest = { consentVoice = null },
            title = { Text("Cloud Processing") },
            text = { Text("Online voice ka preview banane ke liye sample text Google Gemini API ko bheja jayega. Jari rakhein?") },
            confirmButton = {
                TextButton(onClick = {
                    app.prefs.cloudConsent = true
                    consentVoice = null
                    preview(cv)
                }) { Text("Haan") }
            },
            dismissButton = { TextButton(onClick = { consentVoice = null }) { Text("Nahi") } },
        )
    }
}
