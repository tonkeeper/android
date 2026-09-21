package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.TextCell
import ui.painterResource
import ui.preview.ThemedPreview
import ui.shortData
import ui.theme.UIKit

internal sealed interface ExpandableItem {
    val id: String
    val name: String
    val depth: Int

    data class Group(
        override val id: String,
        override val name: String,
        override val depth: Int,
        val children: List<ExpandableItem>,
    ) : ExpandableItem

    data class Property(
        override val id: String,
        override val name: String,
        override val depth: Int,
        val value: String,
    ) : ExpandableItem
}

internal fun parseExpandableJson(raw: String): List<ExpandableItem>? {
    return try {
        expandableFromObject(JSONObject(raw))
    } catch (_: JSONException) {
        try {
            expandableFromArray(JSONArray(raw))
        } catch (_: JSONException) {
            null
        }
    }
}

internal fun expandableFromObject(json: JSONObject, root: String = "", depth: Int = 0): List<ExpandableItem> {
    val keys = json.keys().asSequence().toList()
    return keys.map { key ->
        val id = "${root}_$key"
        when (val value = json.get(key)) {
            is JSONObject -> ExpandableItem.Group(id, key, depth, expandableFromObject(value, id, depth + 1))
            is JSONArray -> ExpandableItem.Group(id, key, depth, expandableFromArray(value, id, depth + 1))
            else -> ExpandableItem.Property(id, key, depth, value.toString())
        }
    }.sortedBy { if (it is ExpandableItem.Group) 0 else 1 }
}

internal fun expandableFromArray(json: JSONArray, root: String = "", depth: Int = 0): List<ExpandableItem> {
    return (0 until json.length()).map { index ->
        val id = "${root}_$index"
        val name = index.toString()
        when (val value = json.get(index)) {
            is JSONObject -> ExpandableItem.Group(id, name, depth, expandableFromObject(value, id, depth + 1))
            is JSONArray -> ExpandableItem.Group(id, name, depth, expandableFromArray(value, id, depth + 1))
            else -> ExpandableItem.Property(id, name, depth, value.toString())
        }
    }
}

@Composable
internal fun ExpandableJsonList(roots: List<ExpandableItem>) {
    val expanded = remember { mutableStateOf(emptySet<String>()) }
    Column {
        RenderExpandable(items = roots, expanded = expanded.value) { id ->
            expanded.value = expanded.value.toMutableSet().apply {
                if (!add(id)) remove(id)
            }
        }
    }
}

@Preview
@Composable
internal fun RenderExpandablePreview(
) {
    ThemedPreview {
        TextCell(
            title = { MoonItemSubtitle("Advanced content") },
            description = {
                ExpandableJsonList(
                    roots = parseExpandableJson("{ \"types\": { \"EIP712Domain\": [ { \"name\": \"name\", \"type\": \"string\" }, { \"name\": \"version\", \"type\": \"string\" }, { \"name\": \"chainId\", \"type\": \"uint256\" }, { \"name\": \"verifyingContract\", \"type\": \"address\" } ], \"Person\": [ { \"name\": \"name\", \"type\": \"string\" }, { \"name\": \"wallet\", \"type\": \"address\" } ], \"Mail\": [ { \"name\": \"from\", \"type\": \"Person\" }, { \"name\": \"to\", \"type\": \"Person\" }, { \"name\": \"contents\", \"type\": \"string\" } ] }, \"primaryType\": \"Mail\", \"domain\": { \"name\": \"Ether Mail\", \"version\": \"1\", \"chainId\": 1, \"verifyingContract\": \"0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC\" }, \"message\": { \"from\": { \"name\": \"Cow\", \"wallet\": \"0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826\" }, \"to\": { \"name\": \"Bob\", \"wallet\": \"0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB\" }, \"contents\": \"Hello, Bob!\" } }")!!,
                )
            }
        )
    }
}

private const val MAX_DEPTH = 3

@Composable
internal fun RenderExpandable(
    items: List<ExpandableItem>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
) {
    items.forEachIndexed { index, item ->
        when (item) {
            is ExpandableItem.Group -> {
                val autoExpand = item.depth >= MAX_DEPTH
                ExpandableTitle(
                    title = item.name,
                    depth = item.depth,
                    isExpanded = autoExpand || item.id in expanded,
                    showChevron = !autoExpand,
                    onToggle = { onToggle(item.id) },
                )
                if (autoExpand || item.id in expanded) {
                    RenderExpandable(item.children, expanded, onToggle)
                }
            }
            is ExpandableItem.Property -> {
                ExpandableProperty(item.name, item.value, item.depth)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ExpandableTitle(
    title: String,
    depth: Int,
    isExpanded: Boolean,
    showChevron: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (showChevron) it.clickable { onToggle() } else it }
            .padding(
                start = (minOf(depth, MAX_DEPTH) * 8).dp,
                top = if (showChevron) 8.dp else 4.dp,
                bottom = if (showChevron) 8.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoonItemSubtitle(
            modifier = Modifier.weight(1f),
            text = title,
            color = UIKit.colorScheme.text.secondary,
        )

        if (showChevron) {
            ExpandedIcon(isExpanded)
        }
    }
}

@Composable
private fun ExpandableProperty(key: String, value: String, depth: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (minOf(depth, MAX_DEPTH) * 8).dp, top = 4.dp, bottom = 4.dp),
    ) {
        MoonItemSubtitle(
            text = "${key}:",
            color = UIKit.colorScheme.text.secondary,
        )
        Spacer(Modifier.width(4.dp))
        MoonItemSubtitle(
            text = value.shortData,
            color = UIKit.colorScheme.text.primary,
            maxLines = 10,
        )
    }
}


@Composable
fun ExpandedIcon(isExpanded: Boolean) {
    MoonItemIcon(
        painter = painterResource(
            if (isExpanded) UIKitIcon.ic_chevron_up_16 else UIKitIcon.ic_chevron_down_16,
        ),
        size = 16.dp,
    )
}