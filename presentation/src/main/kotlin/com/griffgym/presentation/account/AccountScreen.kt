package com.griffgym.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.presentation.components.CardHeader
import com.griffgym.presentation.components.GriffGymBadge
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.GriffGymSecondaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * The account screen as the app shell mounts it.
 *
 * [onCreateAccount] and [onSignIn] are handed back to the host rather than navigated to
 * from here: the auth flow is a graph of its own ([AuthNavHost]) and the shell decides how
 * to present it. [onSignedOut] fires only after the session and this account's cached
 * training data are actually gone from the device.
 */
@Composable
fun AccountRoute(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.signedOut.collect { onSignedOut() }
    }

    AccountScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onCreateAccount = onCreateAccount,
        onSignIn = onSignIn,
        modifier = modifier,
    )
}

/**
 * Two quite different screens behind one route, because they answer the same question.
 *
 * Local-only leads with what is missing, in the app's warning language, and offers the one
 * thing that fixes it. Signed in leads with the reassurance and keeps the two maintenance
 * actions — sync and sign out — below it, where they are reachable but not prominent.
 */
@Composable
fun AccountScreen(
    state: AccountUiState,
    onEvent: (AccountUiEvent) -> Unit,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    val margin = GriffGymTheme.dimens.screenMargin

    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primary)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(margin),
        verticalArrangement = Arrangement.spacedBy(GriffGymTheme.dimens.sectionSpacing),
    ) {
        when (state.mode) {
            AccountMode.LOCAL_ONLY -> LocalOnlySection(
                onCreateAccount = onCreateAccount,
                onSignIn = onSignIn,
            )

            AccountMode.AUTHENTICATED -> AuthenticatedSection(
                state = state,
                onEvent = onEvent,
            )
        }

        if (state.message != null) {
            AccountErrorText(state.message)
        }
    }

    if (state.isConfirmingSignOut) {
        SignOutDialog(
            onConfirm = { onEvent(AccountUiEvent.ConfirmSignOut) },
            onDismiss = { onEvent(AccountUiEvent.DismissSignOut) },
        )
    }
}

@Composable
private fun LocalOnlySection(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    val colors = GriffGymTheme.colors

    GriffGymCard(accentBar = colors.primary) {
        GriffGymBadge(text = "LOCAL ONLY", color = colors.textTertiary)
        Spacer(Modifier.height(12.dp))
        Text(
            text = BackupStatusUi.NOT_BACKED_UP.label,
            style = GriffGymTheme.typography.displayMedium,
            color = colors.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = AccountCopy.LOCAL_ONLY_EXPLANATION,
            style = GriffGymTheme.typography.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(20.dp))
        GriffGymPrimaryButton(
            text = "CREATE ACCOUNT",
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Already have an account?",
                style = GriffGymTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
        }
        AccountTertiaryAction(text = "SIGN IN", onClick = onSignIn)
    }
}

@Composable
private fun AuthenticatedSection(
    state: AccountUiState,
    onEvent: (AccountUiEvent) -> Unit,
) {
    val colors = GriffGymTheme.colors

    GriffGymCard {
        Text(
            text = "SIGNED IN AS",
            style = GriffGymTheme.typography.label,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.email.orEmpty(),
            style = GriffGymTheme.typography.title,
            color = colors.textPrimary,
        )
    }

    GriffGymCard {
        CardHeader(
            title = "CLOUD BACKUP",
            action = {
                GriffGymBadge(
                    text = state.status.label,
                    filled = state.status.isReassuring,
                    color = when (state.status) {
                        BackupStatusUi.FAILED -> colors.error
                        BackupStatusUi.OFFLINE -> colors.textTertiary
                        else -> colors.primary
                    },
                )
            },
        )
        Spacer(Modifier.height(14.dp))

        if (state.lastSyncLabel != null) {
            Text(
                text = "Last sync — ${state.lastSyncLabel}",
                style = GriffGymTheme.typography.dataSmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
        }

        Text(
            text = if (state.isOffline) {
                AccountCopy.OFFLINE_EXPLANATION
            } else {
                AccountCopy.BACKED_UP_EXPLANATION
            },
            style = GriffGymTheme.typography.bodySmall,
            color = colors.textTertiary,
        )

        Spacer(Modifier.height(20.dp))
        GriffGymPrimaryButton(
            text = if (state.isSyncing) "SYNCING…" else "SYNC NOW",
            onClick = { onEvent(AccountUiEvent.SyncNow) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSyncing,
        )
        Spacer(Modifier.height(8.dp))
        GriffGymSecondaryButton(
            text = "SIGN OUT",
            onClick = { onEvent(AccountUiEvent.SignOutRequested) },
            modifier = Modifier.fillMaxWidth(),
            color = colors.error,
        )
    }
}

/**
 * Sign-out is the one destructive-looking action on the screen that is not destructive at
 * all, so the confirmation spends its words saying so. Somebody who thinks signing out
 * deletes their training history will simply never sign out — including on a phone they
 * are about to sell.
 */
@Composable
private fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = GriffGymTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        AccountDialogSurface {
            Text(
                text = AccountCopy.SIGN_OUT_TITLE,
                style = GriffGymTheme.typography.headline,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = AccountCopy.SIGN_OUT_KEEPS_CLOUD,
                style = GriffGymTheme.typography.body,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = AccountCopy.SIGN_OUT_LOCAL_EFFECT,
                style = GriffGymTheme.typography.body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                GriffGymSecondaryButton(
                    text = "BACK",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.height(12.dp))
                GriffGymPrimaryButton(
                    text = "SIGN OUT",
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
            }
        }
    }
}

internal object AccountCopy {

    const val LOCAL_ONLY_EXPLANATION = "Your training history is stored on this phone only. " +
        "If you uninstall Griff Gym, clear the app data, or lose this device, it cannot be " +
        "recovered."

    const val BACKED_UP_EXPLANATION = "Every workout you log is copied to your account in " +
        "the background."

    const val OFFLINE_EXPLANATION =
        "OFFLINE — Changes will be backed up when your connection returns."

    const val SIGN_OUT_TITLE = "SIGN OUT?"

    const val SIGN_OUT_KEEPS_CLOUD = "Your cloud backup is kept. Signing out does not delete " +
        "your account or your training history."

    const val SIGN_OUT_LOCAL_EFFECT = "This device's copy is removed, and comes back the next " +
        "time you sign in."
}

@Preview(widthDp = 390, heightDp = 700, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun AccountScreenLocalOnlyPreview() {
    GriffGymTheme {
        Box(Modifier.background(GriffGymTheme.colors.background)) {
            AccountScreen(
                state = AccountUiState(isLoading = false),
                onEvent = {},
                onCreateAccount = {},
                onSignIn = {},
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 700, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun AccountScreenAuthenticatedPreview() {
    GriffGymTheme {
        Box(Modifier.background(GriffGymTheme.colors.background)) {
            AccountScreen(
                state = AccountUiState(
                    isLoading = false,
                    mode = AccountMode.AUTHENTICATED,
                    email = "lifter@griffgym.app",
                    status = BackupStatusUi.BACKED_UP,
                    lastSyncLabel = "Today, 18:42",
                ),
                onEvent = {},
                onCreateAccount = {},
                onSignIn = {},
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 700, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun AccountScreenOfflinePreview() {
    GriffGymTheme {
        Box(Modifier.background(GriffGymTheme.colors.background)) {
            AccountScreen(
                state = AccountUiState(
                    isLoading = false,
                    mode = AccountMode.AUTHENTICATED,
                    email = "lifter@griffgym.app",
                    status = BackupStatusUi.OFFLINE,
                    lastSyncLabel = "Yesterday, 07:15",
                ),
                onEvent = {},
                onCreateAccount = {},
                onSignIn = {},
            )
        }
    }
}
