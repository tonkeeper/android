package ui.components.moon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.painterResource
import ui.theme.Dimens
import ui.theme.UIKit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonTopAppBarSimple(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,

    navigationIconRes: Int? = null,
    onNavigationClick: (() -> Unit)? = null,

    actionIconRes: Int? = null,
    onActionClick: (() -> Unit)? = null,

    scrollBehavior: TopAppBarScrollBehavior? = null,

    headerHeight: Dp = Dimens.heightBar,
    windowInsets: WindowInsets = remember { WindowInsets() },
    backgroundColor: Color = UIKit.colorScheme.background.page,
    iconTintColor: Color = UIKit.colorScheme.buttonSecondary.primaryForeground,
    iconBackgroundColor: Color = UIKit.colorScheme.buttonSecondary.primaryBackground,
) {

    CenterAlignedTopAppBar(
        scrollBehavior = scrollBehavior,
        windowInsets = windowInsets,
        modifier = modifier.padding(horizontal = 16.dp),
        expandedHeight = headerHeight,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = backgroundColor,
            scrolledContainerColor = backgroundColor,
        ),
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = UIKit.colorScheme.text.primary,
                    style = UIKit.typography.h3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!subtitle.isNullOrBlank() ) {
                    Text(
                        text = subtitle,
                        color = UIKit.colorScheme.text.secondary,
                        style = UIKit.typography.body2.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (navigationIconRes != null) {
                MoonActionIcon(
                    modifier = Modifier.layoutId("navigation"),
                    painter = painterResource(id = navigationIconRes),
                    onClick = onNavigationClick,
                    tintColor = iconTintColor,
                    backgroundColor = iconBackgroundColor,
                    contentDescription = "Navigation"
                )
            }
        },
        actions = {
            if (actionIconRes != null && onActionClick != null) {
                MoonActionIcon(
                    painter = painterResource(id = actionIconRes),
                    onClick = onActionClick,
                    tintColor = iconTintColor,
                    backgroundColor = iconBackgroundColor,
                    contentDescription = "Action"
                )
            }
        }
    )
}
