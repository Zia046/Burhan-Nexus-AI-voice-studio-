package com.burhannexus.voicestudio.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.burhannexus.voicestudio.App
import com.burhannexus.voicestudio.audio.Mp3
import com.burhannexus.voicestudio.data.Project
import com.burhannexus.voicestudio.gen.GenStatus
import com.burhannexus.voicestudio.tts.TextProcessor
import com.burhannexus.voicestudio.tts.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

fun safeFileName(name: String): String =
    name.trim().replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(40).ifBlank { "audio" }

fun shareAudio(ctx: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (file.name.endsWith(".mp3")) "audio/mpeg" else "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share audio"))
    } catch (e: Exception) {
        toast(ctx, "Share nahi ho saka: ${e.message}")
    }
}

@Composable
fun StudioScreen(nav: NavController, initialId: Long) {
    val app = App.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = app.db.projects()

    var projectId by rememberSaveable { mutableStateOf(initialId) }
    var loaded by rememberSaveable { mutableStateOf(initialId == 0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var voiceId by rememberSaveable { mutableStateOf(app.prefs.defaultVoiceId) }
    var emotion by rememberSaveable { mutableStateOf(app.prefs.defaultEmotion) }
    var intensity by rememberSaveable { mutableStateOf(60f) }
    var speed by rememberSaveable { mutableStateOf(app.prefs.defaultSpeed) }
    var pitch by rememberSaveable { mutableStateOf(app.prefs.defaultPitch) }

    var project by remember { mutableStateOf<Project?>(null) }
    var showVoices by remember { mutableStateOf(false) }
    var showEmotions by remember { mutableStateOf(false) }
    var consentFor by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf("") }

    val gen by app.generator.state.collectAsState()
    val playing by app.player.playing.collectAsState()
    val revision by app.models.revision.collectAsState()
    val voices by produceState(initialValue = emptyList<Voice>(), revision) { value = app.engines.allVoices() }

    val text = field.text
    val voice = voices.firstOrNull { it.id == voiceId }
    val engine = voice?.let { app.engines.engine(it.engineId) }
    val mine = projectId > 0 && gen.projectId == projectId && gen.status != GenStatus.IDLE

    LaunchedEffect(projectId, gen.status) {
        if (projectId > 0) {
            val p = dao.get(projectId)
            project = p
            if (!loaded && p != null) {
                name = p.name
                field = TextFieldValue(p.script)
                voiceId = p.voiceId
                emotion = p.emotion
                intensity = p.intensity.toFloat()
                speed = p.speed
                pitch = p.pitch
                loaded = true
            }
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun copyTo(uri: android.net.Uri?, source: File) {
        if (uri == null) return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                        ?: throw IllegalStateException("File khul nahi saki")
                }
                toast(ctx, "Export kamyab: ${source.name.substringAfterLast('.').uppercase()} file save ho gayi")
            } catch (e: Exception) {
                toast(ctx, "Export nakaam: ${e.message}")
            }
        }
    }

    val wavSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        copyTo(uri, app.generator.finalWav(projectId))
    }
    val mp3Saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri ->
        copyTo(uri, app.generator.finalMp3(projectId))
    }

    suspend fun ensureMp3(): File? {
        val wav = app.generator.finalWav(projectId)
        val mp3 = app.generator.finalMp3(projectId)
        if (mp3.exists() && mp3.length() > 0 && mp3.lastModified() >= wav.lastModified()) return mp3
        busy = "MP3 encode ho rahi hai..."
        return try {
            withContext(Dispatchers.Default) { Mp3.encode(wav, mp3, app.prefs.mp3Bitrate) }
            mp3
        } catch (e: Throwable) {
            mp3.delete()
            toast(ctx, "MP3 encode nakaam: ${e.message ?: e.javaClass.simpleName}")
            null
        } finally {
            busy = ""
        }
    }

    fun startGeneration(fresh: Boolean) {
        scope.launch {
            val v = voice
            if (v == null) {
                toast(ctx, "Pehle voice chunein")
                return@launch
            }
            if (text.isBlank()) {
                toast(ctx, "Pehle script likhein")
                return@launch
            }
            if (app.generator.state.value.active) {
                toast(ctx, "Ek project pehle se ban raha hai - us ke mukammal hone ka intezar karein")
                return@launch
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            app.player.stop()
            val eng = app.engines.engineOf(v)
            val now = System.currentTimeMillis()
            val title = name.ifBlank { text.trim().replace('\n', ' ').take(28) }
            val existing = if (projectId > 0) dao.get(projectId) else null
            val base = existing ?: Project(
                name = title, script = text, voiceId = v.id, voiceName = v.name, engineName = eng.name, online = eng.online,
                emotion = emotion, intensity = intensity.toInt(), speed = speed, pitch = pitch, status = "draft",
                totalChunks = 0, doneChunks = 0, audioPath = "", durationMs = 0, createdAt = now, updatedAt = now,
            )
            val p = base.copy(
                name = title, script = text, voiceId = v.id, voiceName = v.name, engineName = eng.name, online = eng.online,
                emotion = emotion, intensity = intensity.toInt(), speed = speed, pitch = pitch, updatedAt = now,
            )
            if (existing == null) projectId = dao.insert(p) else dao.update(p)
            name = title
            loaded = true
            app.generator.start(projectId, fresh)
        }
    }

    fun requestGeneration(fresh: Boolean) {
        if (engine?.online == true && !app.prefs.cloudConsent) consentFor = fresh else startGeneration(fresh)
    }

    // ---- derived info ----
    val maxChars = engine?.maxChunkChars ?: 220
    val parsed = remember(text, maxChars) { TextProcessor.parse(text, maxChars) }
    val hasTags = remember(text) { parsed.segments.any { it is com.burhannexus.voicestudio.tts.Segment.Speech && it.emotion != null } }
    val warnings = ArrayList<String>()
    if (voices.isEmpty()) warnings.add("Koi voice dastiyab nahi. AI Model Manager se offline model install karein, ya Settings mein Gemini API key dalein.")
    if (voice != null && engine != null) {
        if (TextProcessor.hasUrdu(text) && !voice.speaks("ur")) warnings.add("Yeh voice Urdu nahi bolti. Urdu ke liye Urdu ya Gemini voice chunein.")
        if (TextProcessor.hasLatin(TextProcessor.parse(text, 100000).segments.filterIsInstance<com.burhannexus.voicestudio.tts.Segment.Speech>().joinToString(" ") { it.text }) && voice.language == "ur" && engine.id == "sherpa") {
            warnings.add("Offline Urdu model English alfaaz theek ada nahi karta. Mixed Urdu + English ke liye Gemini voice behtar hai.")
        }
        if (!engine.supportsEmotion && (emotion != "None" || hasTags)) {
            warnings.add("Is engine ka model emotions support nahi karta - emotion aur emotion tags nazar-andaz honge ([Pause] tag chalega). Asli emotions ke liye Online Gemini voice chunein.")
        }
        if (engine.online && app.prefs.geminiKey.isBlank()) warnings.add("Online voice ke liye Settings > API Configuration mein Gemini API key zaroori hai.")
    }
    if (parsed.unknownTags.isNotEmpty()) warnings.add("Na-maloom tags (nazar-andaz honge): " + parsed.unknownTags.joinToString(", ") { "[$it]" })

    AppScreen(title = "Text to Speech Studio", onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                label = { Text("Project name") }, modifier = Modifier.fillMaxWidth(),
            )

            // ---- Script editor (auto right-to-left for Urdu) ----
            CompositionLocalProvider(LocalLayoutDirection provides if (TextProcessor.hasUrdu(text)) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = { Text("Script / اسکرپٹ") },
                    placeholder = { Text("Yahan apna script likhein ya paste karein...") },
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, lineHeight = 30.sp, textDirection = TextDirection.Content),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 380.dp),
                )
            }
            Text(
                "${text.length} characters  |  ${TextProcessor.countWords(text)} words  |  ${parsed.segments.size} chunks  |  andazan ${fmtDuration(text.length * 1000L / 14)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val tags = listOf("Pause: 1 seconds") + TextProcessor.EMOTIONS
                tags.forEach { tag ->
                    AssistChip(onClick = {
                        val insert = "[$tag] "
                        val at = field.selection.start.coerceIn(0, text.length)
                        field = TextFieldValue(text.substring(0, at) + insert + text.substring(at), TextRange(at + insert.length))
                    }, label = { Text("[$tag]") })
                }
            }

            // ---- Voice + mode ----
            BnCard {
                Text("Voice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (voice == null) {
                    Text("Koi voice muntakhib nahi", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(voice.name, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Badge(if (engine?.online == true) "Online Mode" else "Offline Mode", if (engine?.online == true) BnAccent else BnSecondary)
                        Badge(voice.gender, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        Badge(voice.language.uppercase(), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(engine?.name ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showVoices = true }, enabled = !mine || !gen.active) { Text("Voice chunein") }
                    OutlinedButton(onClick = { nav.navigate("models") }) { Text("Model Manager") }
                }
            }

            // ---- Emotion, speed, pitch ----
            BnCard {
                Text("Emotion & Voice Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Emotion:", modifier = Modifier.width(80.dp))
                    Box {
                        OutlinedButton(onClick = { showEmotions = true }) { Text(emotion) }
                        DropdownMenu(expanded = showEmotions, onDismissRequest = { showEmotions = false }) {
                            (listOf("None") + TextProcessor.EMOTIONS).forEach { e ->
                                DropdownMenuItem(text = { Text(e) }, onClick = { emotion = e; showEmotions = false })
                            }
                        }
                    }
                }
                val emotionOk = engine?.supportsEmotion == true
                Text(
                    if (emotionOk) "Intensity: ${intensity.toInt()}%" else "Intensity (sirf emotion-capable engine ke sath)",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                )
                Slider(value = intensity, onValueChange = { intensity = it }, valueRange = 1f..100f, enabled = emotionOk && emotion != "None")
                Text("Speed: ${"%.2f".format(speed)}x" + if (engine?.online == true) "  (cloud: taqreeban - slow / normal / fast)" else "", style = MaterialTheme.typography.bodySmall)
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.5f..2.0f)
                val pitchOk = engine?.supportsPitch == true
                Text(if (pitchOk) "Pitch: ${"%.2f".format(pitch)}" else "Pitch (sirf Phone System TTS mein dastiyab)", style = MaterialTheme.typography.bodySmall)
                Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.5f..2.0f, enabled = pitchOk)
            }

            // ---- Warnings ----
            if (warnings.isNotEmpty()) {
                BnCard {
                    warnings.forEach { w ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(w, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ---- Generate controls ----
            val partial = project?.status == "failed" || project?.status == "stopped"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when {
                    mine && gen.status == GenStatus.RUNNING -> {
                        Button(onClick = { app.generator.pause() }, modifier = Modifier.weight(1f)) { Text("Pause") }
                        OutlinedButton(onClick = { app.generator.stop() }, modifier = Modifier.weight(1f)) { Text("Stop") }
                    }
                    mine && gen.status == GenStatus.PAUSED -> {
                        Button(onClick = { app.generator.resume() }, modifier = Modifier.weight(1f)) { Text("Resume") }
                        OutlinedButton(onClick = { app.generator.stop() }, modifier = Modifier.weight(1f)) { Text("Stop") }
                    }
                    else -> {
                        Button(
                            onClick = { requestGeneration(false) },
                            enabled = voice != null && text.isNotBlank() && !gen.active,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) { Text(if (partial) "Resume (wahin se)" else "Generate") }
                        if (project != null) {
                            OutlinedButton(
                                onClick = { requestGeneration(true) },
                                enabled = voice != null && text.isNotBlank() && !gen.active,
                                modifier = Modifier.weight(1f),
                            ) { Text("Regenerate") }
                        }
                    }
                }
            }
            if (gen.active && !mine) {
                Text("Doosra project abhi ban raha hai (${gen.percent}%).", style = MaterialTheme.typography.bodySmall)
            }

            // ---- Progress + chunk previews ----
            if (mine) {
                BnCard {
                    Text("Generation: ${gen.status}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { gen.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("${gen.percent}%  |  ${gen.done} / ${gen.total} chunks" + if (gen.active) "  |  baqi waqt: ${fmtEta(gen.etaSec)}" else "", fontWeight = FontWeight.SemiBold)
                    Text(gen.message, style = MaterialTheme.typography.bodySmall)
                    if (gen.labels.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Har hissay ki preview", style = MaterialTheme.typography.labelLarge)
                        gen.labels.forEachIndexed { i, label ->
                            val file = app.generator.chunkFile(projectId, i)
                            val ready = file.exists() && file.length() > 44
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    if (ready) Icons.Filled.CheckCircle else Icons.Filled.HourglassEmpty,
                                    contentDescription = null,
                                    tint = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${i + 1}. $label" + if (gen.current == i) "  (ban raha hai)" else "",
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { app.player.toggle(file) }, enabled = ready) {
                                    Icon(if (playing == file.path) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = "Preview")
                                }
                            }
                        }
                    }
                }
            }

            // ---- Final audio ----
            val finalWav = app.generator.finalWav(projectId)
            val p = project
            if (projectId > 0 && p != null && p.status == "done" && finalWav.exists()) {
                BnCard {
                    Text("Mukammal Audio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Duration ${fmtDuration(p.durationMs)}  |  WAV ${fmtBytes(finalWav.length())}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { app.player.toggle(finalWav) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (playing == finalWav.path) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (playing == finalWav.path) "Stop" else "Play full audio")
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { wavSaver.launch(safeFileName(p.name) + ".wav") }, modifier = Modifier.weight(1f)) { Text("WAV") }
                        OutlinedButton(
                            onClick = { scope.launch { if (ensureMp3() != null) mp3Saver.launch(safeFileName(p.name) + ".mp3") } },
                            enabled = busy.isEmpty(), modifier = Modifier.weight(1f),
                        ) { Text("MP3") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val f = if (app.prefs.exportFormat == "mp3") ensureMp3() else finalWav
                                    if (f != null) shareAudio(ctx, f)
                                }
                            },
                            enabled = busy.isEmpty(), modifier = Modifier.weight(1f),
                        ) { Text("Share") }
                    }
                    if (busy.isNotEmpty()) Text(busy, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showVoices) {
        VoicePickerDialog(voices = voices, selected = voiceId, onDismiss = { showVoices = false }) {
            voiceId = it.id
            showVoices = false
        }
    }

    val consent = consentFor
    if (consent != null) {
        AlertDialog(
            onDismissRequest = { consentFor = null },
            title = { Text("Cloud Processing") },
            text = {
                Text(
                    "Online Mode mein aap ka script internet ke zariye Google Gemini API ko bheja jata hai aur audio wahan banti hai. " +
                        "Google free tier ka data apni services behtar banane ke liye istemal kar sakta hai. " +
                        "Aap ki recordings ya voice samples kabhi upload nahi hote. Kya aap jari rakhna chahte hain?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    app.prefs.cloudConsent = true
                    consentFor = null
                    startGeneration(consent)
                }) { Text("Haan, jari rakhein") }
            },
            dismissButton = { TextButton(onClick = { consentFor = null }) { Text("Nahi") } },
        )
    }
}
