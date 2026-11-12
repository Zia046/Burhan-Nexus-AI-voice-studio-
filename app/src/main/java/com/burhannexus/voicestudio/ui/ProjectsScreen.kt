package com.burhannexus.voicestudio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.burhannexus.voicestudio.App
import com.burhannexus.voicestudio.data.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(nav: NavController) {
    val app = App.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = app.db.projects()
    val projects by dao.all().collectAsState(initial = emptyList())
    val gen by app.generator.state.collectAsState()
    val playing by app.player.playing.collectAsState()
    var renaming by remember { mutableStateOf<Project?>(null) }
    var deleting by remember { mutableStateOf<Project?>(null) }
    var newName by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    AppScreen(title = "My Projects", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Button(onClick = { nav.navigate("studio/0") }, modifier = Modifier.fillMaxWidth()) { Text("Naya project") }
            }
            if (projects.isEmpty()) {
                item { Text("Abhi koi project mehfooz nahi.", modifier = Modifier.padding(top = 12.dp)) }
            }
            items(projects, key = { it.id }) { p ->
                val wav = app.generator.finalWav(p.id)
                val hasAudio = p.status == "done" && wav.exists()
                BnCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Badge(p.status, if (p.status == "done") BnSecondary else BnAccent)
                    }
                    Text(p.script.replace('\n', ' '), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    LabelRow("Voice", p.voiceName)
                    LabelRow("Mode", (if (p.online) "Online" else "Offline") + " - " + p.engineName)
                    LabelRow("Emotion", if (p.emotion == "None") "None" else "${p.emotion} (${p.intensity}%)")
                    LabelRow("Audio", if (hasAudio) fmtDuration(p.durationMs) else "${p.doneChunks} / ${p.totalChunks} chunks")
                    LabelRow("Bana", dateFormat.format(Date(p.createdAt)))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { nav.navigate("studio/${p.id}") }, modifier = Modifier.weight(1f)) { Text("Open") }
                        if (hasAudio) {
                            OutlinedButton(onClick = { app.player.toggle(wav) }, modifier = Modifier.weight(1f)) {
                                Text(if (playing == wav.path) "Stop" else "Play")
                            }
                            OutlinedButton(onClick = { shareAudio(ctx, wav) }, modifier = Modifier.weight(1f)) { Text("Share") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { newName = p.name; renaming = p }) { Text("Rename") }
                        TextButton(onClick = { deleting = p }, enabled = !(gen.active && gen.projectId == p.id)) { Text("Delete") }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    val r = renaming
    if (r != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename project") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it.take(60) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty()) scope.launch { dao.update(r.copy(name = name, updatedAt = System.currentTimeMillis())) }
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    val d = deleting
    if (d != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Project delete karein?") },
            text = { Text("'${d.name}' ka script aur audio phone se mustaqil hata diye jayenge.") },
            confirmButton = {
                TextButton(onClick = {
                    app.player.stop()
                    scope.launch {
                        withContext(Dispatchers.IO) { app.generator.projectDir(d.id).deleteRecursively() }
                        dao.delete(d)
                    }
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
