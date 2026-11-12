package com.burhannexus.voicestudio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.burhannexus.voicestudio.App
import com.burhannexus.voicestudio.R
import com.burhannexus.voicestudio.gen.GenStatus
import com.burhannexus.voicestudio.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class Tile(val title: String, val icon: ImageVector, val route: String, val pending: Boolean = false)

@Composable
fun HomeScreen(nav: NavController) {
    val app = App.instance
    val gen by app.generator.state.collectAsState()
    val projects by app.db.projects().all().collectAsState(initial = emptyList())
    val revision by app.models.revision.collectAsState()
    val storage by produceState(initialValue = Pair(0L, 0L), projects.size, revision) {
        value = withContext(Dispatchers.IO) {
            Pair(ModelManager.dirSize(File(app.filesDir, "projects")), app.models.totalSize())
        }
    }

    val tiles = listOf(
        Tile("Text to Speech", Icons.Filled.RecordVoiceOver, "studio/0"),
        Tile("AI Voice Library", Icons.Filled.LibraryMusic, "voices"),
        Tile("Voice Cloning", Icons.Filled.Mic, "pending/cloning", pending = true),
        Tile("Emotion Studio", Icons.Filled.EmojiEmotions, "pending/emotion"),
        Tile("Multi Speaker Studio", Icons.Filled.Groups, "pending/multispeaker", pending = true),
        Tile("Audio Editor", Icons.Filled.ContentCut, "pending/editor", pending = true),
        Tile("My Projects", Icons.Filled.Folder, "projects"),
        Tile("Downloaded Voices", Icons.Filled.Download, "models"),
        Tile("AI Model Manager", Icons.Filled.Memory, "models"),
        Tile("Settings", Icons.Filled.Settings, "settings"),
        Tile("About Burhan Nexus", Icons.Filled.Info, "about"),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Brand header - the official logo is designed for a black background and is shown unmodified.
        Surface(color = Color.Black, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.bn_logo),
                    contentDescription = "Burhan Nexus",
                    modifier = Modifier.fillMaxWidth(0.72f).height(64.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("AI Voice Studio", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Your Voice. Your Emotion. Your Studio.", color = BnAccent, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Processing status
        BnCard {
            Text("Processing Status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (gen.status == GenStatus.IDLE) {
                Text("Koi kaam jari nahi.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("${gen.status} - ${gen.percent}%  (${gen.done}/${gen.total} chunks)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { gen.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(gen.message, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Project kholein",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp).clickable { nav.navigate("studio/${gen.projectId}") },
                )
            }
        }

        // Feature tiles, two per row
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { t ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier.weight(1f).height(108.dp).clip(RoundedCornerShape(18.dp)).clickable { nav.navigate(t.route) },
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (t.pending) BnAccent else BnSecondary),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(t.icon, contentDescription = null, tint = BnText) }
                                if (t.pending) Badge("Pending", BnAccent)
                            }
                            Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // Recent projects / generated audio
        BnCard {
            Text("Recent Projects", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (projects.isEmpty()) {
                Text("Abhi koi project nahi. 'Text to Speech' se pehla project banayein.", style = MaterialTheme.typography.bodyMedium)
            }
            projects.take(4).forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate("studio/${p.id}") }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${p.voiceName} - " + if (p.status == "done") "audio ${fmtDuration(p.durationMs)}" else p.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Badge(p.status, if (p.status == "done") BnSecondary else BnAccent)
                }
            }
        }

        // Storage
        BnCard {
            Text("Storage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LabelRow("Projects", "${fmtBytes(storage.first)}  (${projects.count { it.status == "done" }} generated audio)")
            LabelRow("AI models", fmtBytes(storage.second))
            LabelRow("Khaali jagah", fmtBytes(app.models.freeBytes()))
        }

        Text(
            "Developed by Burhan Nexus  |  03475093446",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
    }
}
