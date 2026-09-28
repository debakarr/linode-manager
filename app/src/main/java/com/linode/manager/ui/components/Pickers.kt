package com.linode.manager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class PickerOption(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val group: String? = null,
)

/**
 * A read-only field that opens a searchable bottom sheet. Works with long
 * lists (regions, images, Linodes) where dropdown menus become unusable on
 * phones.
 */
@Composable
fun PickerField(
    label: String,
    options: List<PickerOption>,
    selectedId: String?,
    onPick: (PickerOption) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    enabled: Boolean = true,
    searchable: Boolean = options.size > 8,
    supportingText: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    androidx.compose.foundation.layout.Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.title ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // The text field swallows clicks; overlay a transparent click target.
        androidx.compose.foundation.layout.Box(
            Modifier
                .matchParentSize()
                .padding(top = 8.dp)
                .alpha(0f)
                .clickable(enabled = enabled) { open = true },
        )
    }
    if (open) {
        PickerSheet(
            title = label,
            options = options,
            selectedId = selectedId,
            searchable = searchable,
            onDismiss = { open = false },
            onPick = {
                open = false
                onPick(it)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
    title: String,
    options: List<PickerOption>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onPick: (PickerOption) -> Unit,
    searchable: Boolean = true,
) {
    var query by remember { mutableStateOf("") }
    val filtered =
        remember(query, options) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) {
                options
            } else {
                options.filter {
                    it.title.lowercase().contains(q) ||
                        it.id.lowercase().contains(q) ||
                        it.subtitle?.lowercase()?.contains(q) == true
                }
            }
        }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (searchable) {
                SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), placeholder = "Search")
            }
            LazyColumn(Modifier.heightIn(max = maxHeight)) {
                var lastGroup: String? = null
                filtered.forEach { opt ->
                    if (opt.group != null && opt.group != lastGroup) {
                        val g = opt.group
                        lastGroup = g
                        item(key = "g-$g") {
                            Text(
                                g,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                    }
                    item(key = "o-${opt.id}") {
                        ListItem(
                            headlineContent = { Text(opt.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = opt.subtitle?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                            trailingContent =
                                if (opt.id == selectedId) {
                                    { Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
                                } else {
                                    null
                                },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.clickable { onPick(opt) }.padding(horizontal = 8.dp),
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    item { EmptyState("No matches", message = "Try a different search.") }
                }
                item { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow) }
            }
        }
    }
}
