package dev.sandbox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.sandbox.app.EngineStats
import dev.sandbox.app.SandView
import dev.sandbox.app.SandboxViewModel
import dev.sandbox.app.Tool
import dev.sandbox.core.Category
import dev.sandbox.core.El

@Composable
fun SandboxScreen(vm: SandboxViewModel) {
    var showMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(vm.selected.category) }
    var sandView by remember { mutableStateOf<SandView?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        val text = vm.message
        if (text != null) {
            snackbar.showSnackbar(text)
            vm.message = null
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        SandView(ctx, vm.engine).also { sandView = it }
                    },
                    update = { view ->
                        view.tool = vm.tool
                        view.element = vm.selectedId
                        view.brushPx = vm.brushPx
                        view.coverage = vm.coverage
                        view.haptics = vm.haptics
                        view.targetCells = vm.targetCells
                        view.onPick = { id -> vm.select(id) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                TopHud(
                    vm = vm,
                    onMenu = { showMenu = true },
                    onScreenshot = { sandView?.let { vm.exportScreenshot(it) } },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            Dock(
                vm = vm,
                category = category,
                onCategory = { category = it },
                onSearch = { showSearch = true },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp))
    }

    if (showSearch) {
        ElementSearchDialog(
            selectedId = vm.selectedId,
            onPick = {
                vm.select(it.id)
                category = it.category
                showSearch = false
            },
            onDismiss = { showSearch = false },
        )
    }
    if (showMenu) {
        MenuSheet(vm) { showMenu = false }
    }
}

@Composable
private fun TopHud(
    vm: SandboxViewModel,
    onMenu: () -> Unit,
    onScreenshot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats: EngineStats = vm.stats
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Color(0xCC10151F),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Sandbox Deluxe", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    text = "${stats.fps} FPS · ${formatCount(stats.particles)} частиц · ${"%.1f".format(stats.stepMs)} мс/шаг",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Surface(color = Color(0xCC10151F), shape = RoundedCornerShape(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.toggleThermal() }) {
                    Icon(
                        Icons.Filled.Thermostat,
                        contentDescription = "Тепловизор",
                        tint = if (vm.thermal) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.togglePause() }) {
                    Icon(
                        if (vm.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (vm.paused) "Продолжить" else "Пауза",
                    )
                }
                if (vm.paused) {
                    IconButton(onClick = { vm.stepOnce() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Шаг")
                    }
                }
                IconButton(onClick = onScreenshot) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Скриншот")
                }
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = "Меню")
                }
            }
        }
    }
}

@Composable
private fun Dock(
    vm: SandboxViewModel,
    category: Category,
    onCategory: (Category) -> Unit,
    onSearch: () -> Unit,
) {
    val landscape = LocalConfiguration.current.screenHeightDp < 480
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    items(Tool.entries.toList()) { tool ->
                        FilterChip(
                            selected = vm.tool == tool,
                            onClick = { vm.setTool(tool) },
                            label = { Text(toolTitle(tool), fontSize = 11.sp) },
                        )
                    }
                }
                Text(
                    text = if (vm.tool == Tool.ERASER) "" else "${vm.brushDp.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!landscape) {
                Slider(
                    value = vm.brushDp,
                    onValueChange = { vm.setBrushDp(it) },
                    valueRange = 4f..120f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(28.dp),
                )
            }

            CategoryRow(selected = category, onSelect = onCategory, modifier = Modifier.padding(top = 2.dp))

            ElementRow(
                category = category,
                selectedId = vm.selectedId,
                onSelect = { vm.select(it.id) },
                onSearch = onSearch,
            )

            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(Color(vm.selected.color), RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = vm.selected.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = vm.selected.info(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun toolTitle(tool: Tool): String = when (tool) {
    Tool.BRUSH -> "Кисть"
    Tool.LINE -> "Линия"
    Tool.RECT -> "Рамка"
    Tool.ERASER -> "Ластик"
    Tool.PICKER -> "Пипетка"
}

private fun formatCount(value: Int): String = if (value >= 10_000) "${value / 1000}к" else value.toString()

/** Exposed for the menu sheet: how many materials exist. */
val materialCount: Int get() = El.PALETTE.size
