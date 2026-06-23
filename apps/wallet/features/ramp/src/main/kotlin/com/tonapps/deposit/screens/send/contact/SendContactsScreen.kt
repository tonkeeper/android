package com.tonapps.deposit.screens.send.contact

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.container.MoonSurface

// TODO
@Composable
fun SendContactsScreen(
    feature: ContactFeature,
    onClose: () -> Unit,
    onAddContact: () -> Unit,
    onEditContact: (contactId: Long) -> Unit,
    onContactSelected: (SendContactResult) -> Unit,
) {
    val state by feature.state.global.observeSafeState()

    LaunchedEffect(Unit) {
        feature.events.collect { event ->
            when (event) {
                is ContactEvent.ContactSelected -> onContactSelected(event.result)
            }
        }
    }

    MoonSurface {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            MoonTopAppBarSimple(
                title = stringResource(Localization.contacts),
                actionIconRes = UIKitIcon.ic_close_16,
                onActionClick = onClose,
                backgroundColor = Color.Transparent,
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                when (val s = state) {
                    is ContactState.Loading -> {
                        item { ContactLoadingCell() }
                    }
                    is ContactState.Data -> {
                        items(s.items, key = { itemKey(it) }) { item ->
                            when (item) {
                                is ContactItem.MyWallet -> MyWalletCell(
                                    item = item,
                                    onClick = { feature.selectContact(item) },
                                )
                                is ContactItem.SavedContact -> SavedContactCell(
                                    item = item,
                                    onClick = { feature.selectContact(item) },
                                    onEdit = { onEditContact(item.contact.id) },
                                    onDelete = { feature.sendAction(ContactAction.DeleteContact(item.contact)) },
                                )
                                is ContactItem.LatestContact -> LatestContactCell(
                                    item = item,
                                    onClick = { feature.selectContact(item) },
                                    onAddToContacts = { onAddContact() },
                                    onHide = { feature.sendAction(ContactAction.HideContact(item.address)) },
                                )
                                is ContactItem.Space -> ContactSpaceCell()
                                is ContactItem.Loading -> ContactLoadingCell()
                            }
                        }
                    }
                }
            }

            MoonButtonCell(
                text = stringResource(Localization.add_contact),
            ) {
                onAddContact()
            }
        }
    }
}

private fun itemKey(item: ContactItem): Any = when (item) {
    is ContactItem.MyWallet -> "wallet_${item.address}"
    is ContactItem.SavedContact -> "saved_${item.contact.id}"
    is ContactItem.LatestContact -> "latest_${item.address}"
    is ContactItem.Space -> "space_${item.hashCode()}"
    is ContactItem.Loading -> "loading"
}

@Composable
fun MyWalletCell(
    item: ContactItem.MyWallet,
    onClick: () -> Unit,
) {
    // TODO: Implement
}

@Composable
fun SavedContactCell(
    item: ContactItem.SavedContact,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // TODO: Implement
}

@Composable
fun LatestContactCell(
    item: ContactItem.LatestContact,
    onClick: () -> Unit,
    onAddToContacts: () -> Unit,
    onHide: () -> Unit,
) {
    // TODO: Implement
}

@Composable
fun ContactSpaceCell() {
    // TODO: Implement
}

@Composable
fun ContactLoadingCell() {
    // TODO: Implement
}

