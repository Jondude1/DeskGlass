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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jondude1.deskglass.MainViewModel
import com.jondude1.deskglass.data.Checklist

private enum class NameDialog { New, Rename }

@Composable
fun ChecklistsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val lists by vm.checklists.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<NameDialog?>(null) }
    val selected = lists.firstOrNull { it.id == selectedId } ?: lists.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        BackBar("Checklists", onBack)
        Row(
            Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            GlassCard(Modifier.width(330.dp).fillMaxHeight()) {
                Column(Modifier.padding(22.dp).fillMaxSize()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (l in lists) {
                            ListRow(l, l.id == selected?.id) { selectedId = l.id }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    GlassButton("+  New list", { dialog = NameDialog.New }, Modifier.fillMaxWidth())
                }
            }

            GlassCard(Modifier.weight(1f).fillMaxHeight()) {
                if (selected == null) {
                    Text(
                        "Create your first list.",
                        color = Glass.InkFaint,
                        fontSize = 24.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    key(selected.id) {
                        ListDetail(
                            vm = vm,
                            list = selected,
                            onAddMany = { items -> items.forEach { vm.addChecklistItem(selected.id, it) } },
                            onRename = { dialog = NameDialog.Rename },
                            onAdd = { vm.addChecklistItem(selected.id, it) },
                            onToggle = { vm.toggleChecklistItem(selected.id, it) },
                            onDeleteItem = { vm.deleteChecklistItem(selected.id, it) },
                            onClearDone = { vm.clearDoneChecklistItems(selected.id) },
                            onDeleteList = { vm.deleteChecklist(selected.id) },
                        )
                    }
                }
            }
        }
    }

    when (dialog) {
        NameDialog.New -> NameDialogView(
            title = "New list",
            initial = "",
            onConfirm = {
                selectedId = vm.addChecklist(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        NameDialog.Rename -> if (selected != null) {
            NameDialogView(
                title = "Rename list",
                initial = selected.name,
                onConfirm = {
                    vm.renameChecklist(selected.id, it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
        null -> Unit
    }
}

@Composable
private fun ListRow(list: Checklist, isSelected: Boolean, onClick: () -> Unit) {
    val done = list.items.count { it.done }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            list.name,
            color = Glass.Ink,
            fontSize = 22.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (list.items.isNotEmpty()) {
            Text("$done/${list.items.size}", color = Glass.InkFaint, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ListDetail(
    vm: MainViewModel,
    onAddMany: (List<String>) -> Unit,
    list: Checklist,
    onRename: () -> Unit,
    onAdd: (String) -> Unit,
    onToggle: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearDone: () -> Unit,
    onDeleteList: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDump by remember { mutableStateOf(false) }
    val hasKey by vm.hasApiKey.collectAsState()
    val done = list.items.count { it.done }

    fun submit() {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            onAdd(text)
            draft = ""
        }
    }

    Column(Modifier.padding(32.dp).fillMaxSize()) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                list.name,
                color = Glass.Ink,
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onRename() },
            )
            if (list.items.isNotEmpty()) {
                Text(
                    "$done of ${list.items.size} done",
                    color = Glass.InkFaint,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (list.items.isEmpty()) {
                Text("Nothing here yet. Add the first item below.", color = Glass.InkFaint, fontSize = 20.sp)
            }
            for (item in list.items) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    CheckCircle(item.done) { onToggle(item.id) }
                    Text(
                        item.text,
                        color = if (item.done) Glass.InkFaint else Glass.Ink,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                onToggle(item.id)
                            },
                    )
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                onDeleteItem(item.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", color = Glass.InkFaint, fontSize = 18.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                placeholder = { Text("Add an item", color = Glass.InkFaint) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = editorFieldColors(),
                modifier = Modifier.weight(1f),
            )
            GlassButton("Add", { submit() }, filled = true)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasKey) GlassButton("Brain dump", { showDump = true })
            if (done > 0) GlassButton("Clear done", onClearDone)
            GlassButton(
                if (confirmDelete) "Tap again to delete" else "Delete list",
                { if (confirmDelete) onDeleteList() else confirmDelete = true },
            )
        }
    }

    if (showDump) {
        BrainDumpDialog(
            vm = vm,
            onItems = {
                onAddMany(it)
                showDump = false
            },
            onDismiss = { showDump = false },
        )
    }
}

@Composable
private fun CheckCircle(done: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (done) Glass.Accent.copy(alpha = 0.9f) else Color.Transparent)
            .border(2.dp, if (done) Glass.Accent else Color.White.copy(alpha = 0.45f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Text("✓", color = Color(0xFF0B1020), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NameDialogView(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.5f)
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF151A33))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(title, color = Glass.Ink, fontSize = 28.sp, fontWeight = FontWeight.Light)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim()) }),
                colors = editorFieldColors(),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                GlassButton("Cancel", onDismiss)
                GlassButton("Save", { if (name.isNotBlank()) onConfirm(name.trim()) }, filled = true)
            }
        }
    }
}

/** Paste a messy list or describe a project; Claude turns it into clean checklist items. */
@Composable
private fun BrainDumpDialog(vm: MainViewModel, onItems: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.6f)
                .widthIn(max = 700.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF151A33))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Brain dump", color = Glass.Ink, fontSize = 28.sp, fontWeight = FontWeight.Light)
            Text(
                "Paste a messy list or describe a project. I'll turn it into clear steps.",
                color = Glass.InkDim,
                fontSize = 18.sp,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 5,
                maxLines = 9,
                colors = editorFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = Color(0xFFFFB4B4), fontSize = 17.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                GlassButton("Cancel", onDismiss)
                GlassButton(
                    if (busy) "Working…" else "Break it down",
                    {
                        if (text.isNotBlank() && !busy) {
                            scope.launch {
                                busy = true
                                error = null
                                try {
                                    val items = vm.ai.breakDown(text)
                                    if (items.isEmpty()) error = "I couldn't find any steps in that." else onItems(items)
                                } catch (e: Exception) {
                                    error = vm.errorMessage(e)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    filled = true,
                )
            }
        }
    }
}
