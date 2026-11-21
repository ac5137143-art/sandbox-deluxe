package dev.sandbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sandbox.app.R
import dev.sandbox.app.SceneInfo
import dev.sandbox.app.SandboxViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(vm: SandboxViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Text(
                text = "Sandbox Deluxe",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(ru("Сцены")) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(ru("Мои сцены")) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(ru("Настройки")) })
            }
            Spacer(Modifier.height(8.dp))
            when (tab) {
                0 -> ScenesTab(vm, onDismiss)
                1 -> SavesTab(vm)
                else -> SettingsTab(vm)
            }
        }
    }
}

private fun ru(text: String) = text

@Composable
private fun ScenesTab(vm: SandboxViewModel, onDismiss: () -> Unit) {
    var pending by remember { mutableStateOf<Int?>(null) }
    val names = remember { vm.sceneNames() }

    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        items(names.indices.toList()) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(names[index], style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (index == 0) "Чистый холст" else "Готовая композиция",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { pending = index }) { Text(ru("Загрузить")) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }

    val index = pending
    if (index != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(ru("Загрузить сцену?")) },
            text = { Text("«${names[index]}» заменит текущую песочницу.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.applyScene(index)
                    pending = null
                    onDismiss()
                }) { Text(ru("Загрузить")) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(ru("Отмена")) } },
        )
    }
}

@Composable
private fun SavesTab(vm: SandboxViewModel) {
    var showName by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<SceneInfo?>(null) }
    val saves = vm.saves
    val formatter = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(ru("Сохранённые песочницы"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { showName = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(ru("Сохранить"))
            }
        }
        if (saves.isEmpty()) {
            Text(
                text = ru("Пока нет сохранённых сцен"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(saves, key = { it.file.absolutePath }) { info ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(info.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${formatter.format(Date(info.modified))} · ${info.bytes / 1024} КБ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.loadScene(info) }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = ru("Загрузить"))
                        }
                        IconButton(onClick = { toDelete = info }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = ru("Удалить"),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    if (showName) {
        AlertDialog(
            onDismissRequest = { showName = false },
            title = { Text(ru("Название сцены")) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    placeholder = { Text(ru("Например, «Вулкан 2»")) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveCurrent(name.ifBlank { "Сцена" })
                    name = ""
                    showName = false
                }) { Text(ru("Сохранить")) }
            },
            dismissButton = { TextButton(onClick = { showName = false }) { Text(ru("Отмена")) } },
        )
    }

    val deleting = toDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(ru("Удалить сцену?")) },
            text = { Text("«${deleting.name}» будет удалена безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteScene(deleting)
                    toDelete = null
                }) { Text(ru("Удалить")) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(ru("Отмена")) } },
        )
    }
}

@Composable
private fun SettingsTab(vm: SandboxViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text(ru("Детализация"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        val qualities = listOf(
            "Низкая — максимум FPS",
            "Средняя — баланс",
            "Высокая — больше частиц",
        )
        qualities.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = vm.quality == index, onClick = { vm.setQuality(index) })
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(ru("Скорость симуляции"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            listOf(0.25f, 0.5f, 1f, 2f, 4f).forEach { value ->
                FilterChip(
                    selected = vm.speed == value,
                    onClick = { vm.setSpeed(value) },
                    label = { Text(if (value < 1f) "${value}×" else "${value.toInt()}×", fontSize = 12.sp) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        ToggleRow("Инвертировать гравитацию", vm.invertGravity) { vm.setInvertGravity(it) }
        ToggleRow("Свечение горячих материалов", vm.glowEnabled) { vm.setGlow(it) }
        ToggleRow("Вибрация при рисовании", vm.haptics) { vm.setHaptics(it) }

        Spacer(Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Sandbox Deluxe — клеточная песочница с теплопроводностью, фазовыми переходами, " +
                    "химией и взрывами. ${dev.sandbox.core.El.PALETTE.size} материалов, тепловизор, " +
                    "сохранение сцен и экспорт скриншотов.\n\nСборка: v1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
