package dev.sandbox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.sandbox.core.Category
import dev.sandbox.core.El
import dev.sandbox.core.Element

/** Round colour swatch + name, used in the palette rows and in the search grid. */
@Composable
fun ElementChip(
    element: Element,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = Color(element.color)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (color.luminance() > 0.55f) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = element.name,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CategoryRow(
    selected: Category,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = remember { Category.entries.filter { c -> El.PALETTE.any { it.category == c } } }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories) { category ->
            FilterChip(
                selected = category == selected,
                onClick = { onSelect(category) },
                label = { Text(category.title, fontSize = 12.sp) },
            )
        }
    }
}

@Composable
fun ElementRow(
    category: Category,
    selectedId: Int,
    onSelect: (Element) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elements = remember(category) { El.PALETTE.filter { it.category == category } }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onSearch, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(elements, key = { it.id }) { element ->
                ElementChip(element, element.id == selectedId) { onSelect(element) }
            }
        }
    }
}

/** Full screen searchable element picker. */
@Composable
fun ElementSearchDialog(
    selectedId: Int,
    onPick: (Element) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) El.PALETTE
        else El.PALETTE.filter { it.name.lowercase().contains(q) || it.key.contains(q) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Название элемента...") },
                        shape = RoundedCornerShape(14.dp),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }
                if (results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(results, key = { it.id }) { element ->
                            ElementChip(element, element.id == selectedId, Modifier.fillMaxWidth()) { onPick(element) }
                        }
                    }
                }
            }
        }
    }
}
