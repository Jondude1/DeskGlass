package com.jondude1.deskglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.MainViewModel
import com.jondude1.deskglass.ai.ChatMessage

private val SUGGESTIONS = listOf(
    "Tell me something interesting",
    "What's on my calendar this week?",
    "Give me a quick brain teaser",
)

@Composable
fun ChatScreen(vm: MainViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val messages by vm.chatMessages.collectAsState()
    val busy by vm.chatBusy.collectAsState()
    val error by vm.chatError.collectAsState()
    val hasKey by vm.hasApiKey.collectAsState()
    var draft by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    // Keep the newest message in view.
    LaunchedEffect(messages.size, busy, error) { scroll.animateScrollTo(scroll.maxValue) }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        draft = ""
        vm.sendChat(t)
    }

    Column(Modifier.fillMaxSize()) {
        BackBar("Chat", onBack) {
            if (messages.isNotEmpty()) GlassButton("New chat", { vm.newChat() })
        }

        if (!hasKey) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassCard(Modifier.widthIn(max = 640.dp).padding(bottom = 80.dp)) {
                    Column(Modifier.padding(44.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("One quick setup step", color = Glass.InkFaint, fontSize = 16.sp, letterSpacing = 4.sp)
                        Text(
                            "Add your Anthropic API key in Settings and we can start chatting.",
                            color = Glass.Ink,
                            fontSize = 26.sp,
                        )
                        GlassButton("Open Settings", onOpenSettings, filled = true)
                    }
                }
            }
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (messages.isEmpty()) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "Hey ${vm.userName.ifBlank { "there" }}. What's on your mind?",
                    color = Glass.InkDim,
                    fontSize = 30.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                for (s in SUGGESTIONS) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { send(s) }
                            .padding(horizontal = 26.dp, vertical = 12.dp)
                    ) {
                        Text(s, color = Glass.Ink, fontSize = 19.sp)
                    }
                }
            }
            for (m in messages) Bubble(m)
            if (busy) Text("…", color = Glass.InkFaint, fontSize = 30.sp, modifier = Modifier.padding(start = 20.dp))
            error?.let {
                Text(
                    it,
                    color = Color(0xFFFFB4B4),
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x33FF6B6B))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message Claude", color = Glass.InkFaint) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(draft) }),
                colors = editorFieldColors(),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.weight(1f),
            )
            GlassButton("Send", { send(draft) }, filled = true)
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    when (m.role) {
        "action" -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "✓  ${m.text}",
                color = Glass.Accent,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Glass.Accent.copy(alpha = 0.12f))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
        "user" -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                m.text,
                color = Glass.Ink,
                fontSize = 21.sp,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Glass.Accent.copy(alpha = 0.26f))
                    .border(1.dp, Glass.Accent.copy(alpha = 0.4f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
        else -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text(
                m.text,
                color = Glass.Ink,
                fontSize = 21.sp,
                lineHeight = 30.sp,
                modifier = Modifier
                    .widthIn(max = 700.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}
