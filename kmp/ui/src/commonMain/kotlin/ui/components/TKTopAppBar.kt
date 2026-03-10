package ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import ui.theme.Dimens
import ui.theme.UIKit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TKTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = UIKit.typography.h2,
                color = UIKit.colorScheme.text.primary,
                textAlign = TextAlign.Center
            )
        },
        expandedHeight = Dimens.heightBar,
        colors = UIKit.colorScheme.topAppBarColors,
        scrollBehavior = scrollBehavior
    )
}