package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonLoadingPreviewImage
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.theme.modifiers.shimmer

/**
 * Placeholder body of the confirm sheet. Lives on its own so the multichain scaffold can show it
 * under the shared action slot instead of duplicating a second scaffold with a second slot.
 */
@Composable
fun SendConfirmShimmerBody(shimmer: Float) {
    MoonLoadingPreviewImage(size = 96.dp)

    Spacer(Modifier.height(16.dp))

    Spacer(
        Modifier
            .fillMaxWidth(0.4f)
            .height(20.dp)
            .shimmer(shimmer)
    )

    Spacer(Modifier.height(4.dp))

    Spacer(
        Modifier
            .fillMaxWidth(0.5f)
            .height(28.dp)
            .shimmer(shimmer)
    )

    Spacer(Modifier.height(16.dp))

    MoonBundleCell {
        Column {
            MoonPropertyBigCell(
                title = {
                    Spacer(
                        Modifier
                            .fillMaxWidth(0.25f)
                            .height(20.dp)
                            .shimmer(shimmer)
                    )
                },
                content = {
                    Spacer(
                        Modifier
                            .fillMaxWidth(0.3f)
                            .height(20.dp)
                            .shimmer(shimmer)
                    )
                },
            )

            MoonItemDivider()

            MoonPropertyBigCell(
                title = {
                    Spacer(
                        Modifier
                            .fillMaxWidth(0.25f)
                            .height(20.dp)
                            .shimmer(shimmer)
                    )
                },
                content = {
                    Spacer(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(20.dp)
                            .shimmer(shimmer)
                    )
                },
            )

            MoonItemDivider()

            MoonPropertyBigCell(
                title = {
                    Spacer(
                        Modifier
                            .fillMaxWidth(0.25f)
                            .height(20.dp)
                            .shimmer(shimmer)
                    )
                },
                content = {
                    Spacer(
                        Modifier
                            .fillMaxWidth(0.3f)
                            .height(20.dp)
                            .shimmer(shimmer)
                    )
                },
                contentDescription = {
                    Spacer(
                        Modifier
                            .fillMaxWidth(0.25f)
                            .height(16.dp)
                            .shimmer(shimmer)
                    )
                },
            )

            MoonItemDivider()

            MoonPropertyBigCell(
                title = {
                    Spacer(
                        Modifier
                            .fillMaxWidth(0.25f)
                            .height(20.dp)
                            .shimmer(shimmer)
                    )
                },
                content = {
                    Spacer(
                        Modifier
                            .fillMaxWidth(0.3f)
                            .height(20.dp)
                            .shimmer(shimmer)
                    )
                },
                contentDescription = {
                    Spacer(
                        Modifier
                            .fillMaxWidth(0.25f)
                            .height(16.dp)
                            .shimmer(shimmer)
                    )
                },
            )
        }
    }
}
