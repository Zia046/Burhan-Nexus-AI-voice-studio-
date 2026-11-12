package com.burhannexus.voicestudio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.burhannexus.voicestudio.App
import com.burhannexus.voicestudio.BuildConfig
import com.burhannexus.voicestudio.R
import com.burhannexus.voicestudio.data.Prefs
import com.burhannexus.voicestudio.model.ModelManager
import com.burhannexus.voicestudio.tts.TextProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(nav: NavController) {
    val app = App.instance
    val prefs = app.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var key by remember { mutableStateOf(prefs.geminiKey) }
    var model by remember { mutableStateOf(prefs.geminiModel) }
    var emotion by remember { mutableStateOf(prefs.defaultEmotion) }
    var showEmotions by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(prefs.defaultSpeed) }
    var pitch by remember { mutableStateOf(prefs.defaultPitch) }
    var format by remember { mutableStateOf(prefs.exportFormat) }
    var bitrate by remember { mutableStateOf(prefs.mp3Bitrate) }
    var consent by remember { mutableStateOf(prefs.cloudConsent) }
    var cacheTick by remember { mutableStateOf(0) }
    val cacheSize by produceState(initialValue = 0L, cacheTick) {
        value = withContext(Dispatchers.IO) { ModelManager.dirSize(ctx.cacheDir) }
    }
    val defaultVoiceName by produceState(initialValue = "", prefs.defaultVoiceId) {
        value = app.engines.findVoice(prefs.defaultVoiceId)?.name ?: ""
    }

    AppScreen(title = "Settings", onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BnCard {
                Text("Light / Dark Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                        FilterChip(selected = prefs.theme == value, onClick = { prefs.updateTheme(value) }, label = { Text(label) })
                    }
                }
                Text("App ki zaban: English + Roman Urdu. Mukammal Urdu interface Pending hai.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            BnCard {
                Text("Defaults", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LabelRow("Default voice", defaultVoiceName.ifBlank { "Muntakhib nahi" })
                OutlinedButton(onClick = { nav.navigate("voices") }) { Text("Voice Library se chunein") }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Default emotion:  ")
                    Box {
                        OutlinedButton(onClick = { showEmotions = true }) { Text(emotion) }
                        DropdownMenu(expanded = showEmotions, onDismissRequest = { showEmotions = false }) {
                            (listOf("None") + TextProcessor.EMOTIONS).forEach { e ->
                                DropdownMenuItem(text = { Text(e) }, onClick = {
                                    emotion = e
                                    prefs.defaultEmotion = e
                                    showEmotions = false
                                })
                            }
                        }
                    }
                }
                Text("Speech speed: ${"%.2f".format(speed)}x", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                Slider(value = speed, onValueChange = { speed = it }, onValueChangeFinished = { prefs.defaultSpeed = speed }, valueRange = 0.5f..2f)
                Text("Pitch: ${"%.2f".format(pitch)}  (sirf Phone System TTS)", style = MaterialTheme.typography.bodySmall)
                Slider(value = pitch, onValueChange = { pitch = it }, onValueChangeFinished = { prefs.defaultPitch = pitch }, valueRange = 0.5f..2f)
            }

            BnCard {
                Text("Export & Audio Quality", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Default export / share format", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("mp3" to "MP3", "wav" to "WAV").forEach { (value, label) ->
                        FilterChip(selected = format == value, onClick = { format = value; prefs.exportFormat = value }, label = { Text(label) })
                    }
                }
                Text("MP3 quality (bitrate)", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(64, 96, 128, 160).forEach { b ->
                        FilterChip(selected = bitrate == b, onClick = { bitrate = b; prefs.mp3Bitrate = b }, label = { Text("$b kbps") })
                    }
                }
            }

            BnCard {
                Text("API Configuration (Online Mode)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Google AI Studio (aistudio.google.com) se apni free Gemini API key banayein aur yahan paste karein. " +
                        "Key sirf isi phone par Android Keystore se encrypt ho kar mehfooz hoti hai aur sirf Google ko request ke sath jati hai.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, singleLine = true, label = { Text("Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it }, singleLine = true, label = { Text("Model name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!prefs.secureStorageAvailable) {
                            toast(ctx, "Is phone par secure storage dastiyab nahi - key save nahi ho saki")
                        } else {
                            prefs.geminiKey = key
                            prefs.geminiModel = model.ifBlank { Prefs.DEFAULT_GEMINI_MODEL }
                            model = prefs.geminiModel
                            toast(ctx, if (key.isBlank()) "API key hata di gayi" else "API key mehfooz ho gayi")
                        }
                    }) { Text("Save") }
                    OutlinedButton(onClick = {
                        key = ""
                        prefs.geminiKey = ""
                        toast(ctx, "API key hata di gayi")
                    }) { Text("Key hatayein") }
                }
            }

            BnCard {
                Text("Privacy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Scripts, projects aur audio sirf is phone ki private app storage mein rehte hain. Offline Mode mein kuch bhi phone se bahar nahi jata. " +
                        "Online Mode mein sirf script ka text Google ko jata hai, aur us se pehle ijazat li jati hai. App mein koi ads ya tracking nahi.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LabelRow("Cloud ijazat", if (consent) "Di ja chuki hai" else "Nahi di gayi")
                OutlinedButton(onClick = { prefs.cloudConsent = false; consent = false }, enabled = consent) { Text("Cloud ijazat wapas lein") }
            }

            BnCard {
                Text("Offline Storage Management", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LabelRow("Cache", fmtBytes(cacheSize) + " (voice previews, temporary files)")
                LabelRow("Models", fmtBytes(app.models.totalSize()))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        app.player.stop()
                        scope.launch {
                            withContext(Dispatchers.IO) { ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                            cacheTick++
                            toast(ctx, "Cache saaf ho gaya")
                        }
                    }) { Text("Clear Cache") }
                    OutlinedButton(onClick = { nav.navigate("models") }) { Text("Model Management") }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun AboutScreen(nav: NavController) {
    AppScreen(title = "About Burhan Nexus", onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = Color.Black, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painter = painterResource(R.drawable.bn_logo), contentDescription = "Burhan Nexus", modifier = Modifier.fillMaxWidth(0.8f).height(72.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Burhan Nexus AI Voice Studio", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Your Voice. Your Emotion. Your Studio.", color = BnAccent)
                    Text("Version ${BuildConfig.VERSION_NAME}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
            BnCard {
                Text("Developed by Burhan Nexus", fontWeight = FontWeight.Bold)
                LabelRow("Mobile", "03475093446")
                LabelRow("Email", "ziaullah.046@gmail.com")
                LabelRow("Website", "posbn.netlify.app")
                LabelRow("Address", "Sahara Center, opposite 2D stand, Jowar Chowk, Buner")
            }
            BnCard {
                Text("Feature status (is version mein)", fontWeight = FontWeight.Bold)
                LabelRow("Tayyar", "Text to Speech studio, long-script chunking + auto join, Pause / Resume / Stop, error ke baad wahin se Resume, " +
                    "Voice Library (live preview), Model Manager, Offline + Online engines, Emotions (Gemini), [Pause] aur emotion tags, " +
                    "Projects, WAV + MP3 export, Share, Light / Dark mode")
                LabelRow("Pending", "Voice Cloning, Multi Speaker Studio, Audio Editor, mukammal Urdu interface, signed release APK")
            }
            BnCard {
                Text("AI engines & licenses", fontWeight = FontWeight.Bold)
                LabelRow("sherpa-onnx", "Apache-2.0 - on-device inference (ONNX Runtime, MIT)")
                LabelRow("Piper", "Engine MIT; har voice ka apna license (MODEL_CARD)")
                LabelRow("Kokoro-82M", "Apache-2.0")
                LabelRow("Gemini TTS", "Google ki API terms; aap ki apni key")
                LabelRow("LAME", "LGPL - MP3 encoder")
            }
        }
    }
}

@Composable
fun PendingScreen(nav: NavController, feature: String) {
    val (title, body) = when (feature) {
        "cloning" -> "Voice Cloning" to
            "Status: PENDING (agla marhala).\n\n" +
            "Haqeeqat: is waqt koi aisa open model nahi jo phone ke andar Urdu voice cloning kar sake. On-device cloning ke models " +
            "(Pocket TTS, ZipVoice) sirf English / Chinese bolte hain. XTTS, F5-TTS aur OpenVoice phone par nahi chalte - in ke liye " +
            "PC ya server chahiye, aur XTTS Urdu ko officially support nahi karta.\n\n" +
            "Mansooba: (1) English ke liye on-device cloning Pocket TTS se - Record / Upload, sample quality checker, noise detection, " +
            "save / rename / delete, aur ijazat ka lazmi iqrar. (2) Urdu cloning ke liye aap ke apne PC par chalne wala server adapter.\n\n" +
            "Is screen par koi naqli button nahi rakha gaya."
        "multispeaker" -> "Multi Speaker Studio" to
            "Status: PENDING (agla marhala).\n\n" +
            "Engine tayyar hai: har line alag voice se ban kar ek file mein jur sakti hai. Baqi kaam is ki screen hai - " +
            "'Speaker 1 (Male): ...' format parhna aur har speaker ke liye voice, emotion, speed aur pitch chunna.\n\n" +
            "Tab tak: TTS Studio mein har kirdar ka hissa alag project mein bana kar export kiya ja sakta hai."
        "editor" -> "Audio Editor" to
            "Status: PENDING (agla marhala).\n\n" +
            "Mansooba: trim, split, merge, volume, speed, fade in / out, background music import + volume, aur WAV / MP3 export.\n\n" +
            "Abhi dastiyab: mukammal audio ka playback aur WAV / MP3 export (TTS Studio aur My Projects mein)."
        else -> "Emotion Studio" to
            "Emotions TTS Studio ke andar dastiyab hain: Emotion selector, Intensity slider aur script mein [Happy], [Sad], [Whispering] jaise tags.\n\n" +
            "Asli emotions sirf Online Gemini voices mein hain, kyun ke sirf wohi model jazbaat ada karta hai. " +
            "Offline models (Piper, Kokoro, Phone TTS) sirf neutral parhte hain - app in mein emotion ka naqli dawa nahi karti aur warning dikhati hai.\n\n" +
            "[Pause: 2 seconds] tag har engine ke sath kaam karta hai.\n\nAlag 'Emotion Studio' screen (emotion presets ke sath): Pending."
    }
    AppScreen(title = title, onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BnCard { Text(body, style = MaterialTheme.typography.bodyMedium) }
            Button(onClick = { nav.navigate("studio/0") }, modifier = Modifier.fillMaxWidth()) { Text("TTS Studio kholein") }
        }
    }
}
