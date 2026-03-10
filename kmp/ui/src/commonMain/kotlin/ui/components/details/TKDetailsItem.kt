package ui.components.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import ui.components.moon.MoonDivider
import ui.theme.Dimens

@Composable
internal fun TKDetailsItem(
    row: UiDetails.Row,
    divider: Boolean,
    onClick: ((UiDetails.Row) -> Unit)? = null
) {
    TKDetailsRow(
        modifier = Modifier
            .clickable(
                enabled = row.clickable,
                role = Role.Button,
                onClick = {
                    onClick?.invoke(row)
                }
            )
            .padding(Dimens.offsetMedium),
        key = row.key,
        value = row.value,
        iconLeft = row.iconLeft,
        secondaryValue = row.secondaryValue,
        spoiler = row.spoiler
    )

    if (divider) {
        MoonDivider(
            modifier = Modifier.padding(start = Dimens.offsetMedium)
        )
    }
}