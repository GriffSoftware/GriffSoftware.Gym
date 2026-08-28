package com.griffgym.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.presentation.components.GriffGymBadge
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.GriffGymSecondaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * The profile screen as the app shell mounts it.
 *
 * Both endings leave through the host rather than through the nav graph: signing out and
 * deleting an account each invalidate everything below [com.griffgym.presentation.navigation.GriffGymRoot],
 * so the subtree is replaced wholesale instead of popped back to.
 */
@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onAccountDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                ProfileNavigationEvent.SignedOut -> onSignedOut()
                ProfileNavigationEvent.AccountDeleted -> onAccountDeleted()
            }
        }
    }

    ProfileScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Who the lifter is signed in as, whether their training is safe, and the two doors out.
 *
 * Three bands, in descending order of how often they are wanted and ascending order of what
 * they cost: the backup status, which is the only reason to open this screen most days; the
 * account section, holding the one reversible exit; and the danger zone, fenced off below a
 * hairline and painted in the warning colour rather than in amber. Amber is the app's "do
 * this" and it is deliberately absent from the bottom of this screen.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEvent: (ProfileUiEvent) -> Unit,
    onBack: () -> Unit,
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
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = margin, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountBackAction(onBack = onBack, modifier = Modifier.padding(end = 4.dp))
            Text(
                text = ProfileCopy.TITLE,
                style = GriffGymTheme.typography.headline,
                color = colors.textPrimary,
            )
        }

        Spacer(Modifier.height(20.dp))
        ProfileIdentity(email = state.email.orEmpty())

        Spacer(Modifier.height(28.dp))
        ProfileSectionHeader(title = ProfileCopy.SECTION_CLOUD_BACKUP)
        Spacer(Modifier.height(12.dp))
        BackupSection(state = state, onSyncNow = { onEvent(ProfileUiEvent.SyncNow) })

        Spacer(Modifier.height(28.dp))
        ProfileSectionHeader(title = ProfileCopy.SECTION_ACCOUNT)
        ProfileActionRow(
            label = "SIGN OUT",
            onClick = { onEvent(ProfileUiEvent.SignOutRequested) },
        )
        HairLine()

        if (state.message != null) {
            Spacer(Modifier.height(12.dp))
            AccountErrorText(state.message)
        }

        Spacer(Modifier.height(28.dp))
        DangerZone(onDelete = { onEvent(ProfileUiEvent.DeleteAccountRequested) })

        Spacer(Modifier.height(24.dp))
    }

    if (state.isConfirmingSignOut) {
        ProfileSignOutDialog(
            onConfirm = { onEvent(ProfileUiEvent.ConfirmSignOut) },
            onDismiss = { onEvent(ProfileUiEvent.DismissSignOut) },
        )
    }

    DeleteAccountDialogs(deletion = state.deletion, onEvent = onEvent)
}

@Composable
private fun ProfileIdentity(email: String) {
    val colors = GriffGymTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The same mark as the top bar's avatar, at the size the screen it leads to deserves.
        // A photo would be a profile field Griff Gym neither has nor wants.
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant)
                .border(GriffGymTheme.dimens.borderWidthStrong, colors.outlineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = email,
            style = GriffGymTheme.typography.title,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = ProfileCopy.ACCOUNT_KIND,
            style = GriffGymTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun BackupSection(state: ProfileUiState, onSyncNow: () -> Unit) {
    val colors = GriffGymTheme.colors

    ProfileValueRow(label = "Status") {
        GriffGymBadge(
            text = state.status.label,
            filled = state.status.isReassuring,
            color = when (state.status) {
                BackupStatusUi.FAILED -> colors.error
                BackupStatusUi.OFFLINE -> colors.textTertiary
                else -> colors.primary
            },
        )
    }

    // Absent rather than "never": a fresh account whose first sync has not landed yet is not
    // a broken one, and a row reading "Last backup — never" looks like a fault.
    if (state.lastBackupLabel != null) {
        HairLine()
        ProfileValueRow(label = "Last backup") {
            Text(
                text = state.lastBackupLabel,
                style = GriffGymTheme.typography.dataSmall,
                color = colors.textSecondary,
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    GriffGymPrimaryButton(
        text = if (state.isSyncing) "SYNCING…" else "SYNC NOW",
        onClick = onSyncNow,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isSyncing,
    )
}

/**
 * Fenced off, and the only part of the app painted in the warning colour end to end.
 *
 * It gets its own heading, its own border and a full sentence about what is destroyed,
 * because the difference between this button and SIGN OUT four rows above it is the
 * difference between coming back tomorrow and not.
 */
@Composable
private fun DangerZone(onDelete: () -> Unit) {
    val colors = GriffGymTheme.colors

    ProfileSectionHeader(
        title = ProfileCopy.SECTION_DANGER_ZONE,
        color = colors.error,
        lineColor = colors.error,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = ProfileCopy.DELETE_EXPLANATION,
        style = GriffGymTheme.typography.bodySmall,
        color = colors.textSecondary,
    )
    Spacer(Modifier.height(16.dp))
    GriffGymSecondaryButton(
        text = "DELETE ACCOUNT",
        onClick = onDelete,
        modifier = Modifier.fillMaxWidth(),
        color = colors.error,
    )
}

/**
 * The two questions, and the report if neither of them led anywhere.
 *
 * Exactly one is ever on screen — [DeleteAccountStage] cannot hold two — and back closes it
 * rather than confirming it, which is the whole reason `onDismissRequest` routes to
 * [ProfileUiEvent.DismissDeleteAccount] and never to the confirm action.
 */
@Composable
private fun DeleteAccountDialogs(
    deletion: DeleteAccountUiState,
    onEvent: (ProfileUiEvent) -> Unit,
) {
    when (deletion.stage) {
        DeleteAccountStage.NONE -> Unit

        DeleteAccountStage.EXPLANATION -> DeleteExplanationDialog(
            onContinue = { onEvent(ProfileUiEvent.DeleteAccountExplained) },
            onDismiss = { onEvent(ProfileUiEvent.DismissDeleteAccount) },
        )

        DeleteAccountStage.CONFIRMATION -> DeleteConfirmationDialog(
            deletion = deletion,
            onInputChange = { onEvent(ProfileUiEvent.DeleteConfirmationChanged(it)) },
            onConfirm = { onEvent(ProfileUiEvent.ConfirmDeleteAccount) },
            onDismiss = { onEvent(ProfileUiEvent.DismissDeleteAccount) },
        )

        DeleteAccountStage.FAILURE -> DeleteFailureDialog(
            failure = deletion.failure ?: DeleteAccountFailureUi.RETRYABLE,
            onRetry = { onEvent(ProfileUiEvent.RetryDeleteAccount) },
            onDismiss = { onEvent(ProfileUiEvent.DismissDeleteAccount) },
        )
    }
}

/** Stage one: the inventory. Nothing is asked for yet, and nothing has happened. */
@Composable
private fun DeleteExplanationDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    val colors = GriffGymTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        AccountDialogSurface {
            Text(
                text = ProfileCopy.DELETE_STAGE_ONE_TITLE,
                style = GriffGymTheme.typography.headline,
                color = colors.error,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = ProfileCopy.DELETE_STAGE_ONE_BODY,
                style = GriffGymTheme.typography.body,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(10.dp))
            ProfileCopy.DELETE_REMOVES.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = "—",
                        style = GriffGymTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item,
                        style = GriffGymTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = ProfileCopy.DELETE_CANNOT_BE_UNDONE,
                style = GriffGymTheme.typography.body,
                color = colors.error,
            )
            Spacer(Modifier.height(20.dp))
            StackedDialogActions(
                primaryText = "CONTINUE",
                onPrimary = onContinue,
                secondaryText = "CANCEL",
                onSecondary = onDismiss,
            )
        }
    }
}

/**
 * Stage two: the phrase.
 *
 * Typing DELETE is not theatre. It is the one thing on this screen that cannot be done by
 * muscle memory or by a phone in a pocket, which is exactly what is wanted in front of an
 * action with no undo.
 *
 * The keyboard is put into capitals so a case-sensitive match costs nobody anything. Back —
 * which is what `onDismissRequest` reports for a dialog — is ignored while the call is in
 * flight: it is already at the server and closing the dialog would only hide it.
 */
@Composable
private fun DeleteConfirmationDialog(
    deletion: DeleteAccountUiState,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GriffGymTheme.colors
    Dialog(onDismissRequest = { if (!deletion.isDeleting) onDismiss() }) {
        AccountDialogSurface {
            Text(
                text = ProfileCopy.DELETE_STAGE_TWO_TITLE,
                style = GriffGymTheme.typography.headline,
                color = colors.error,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = ProfileCopy.DELETE_STAGE_TWO_INSTRUCTION,
                style = GriffGymTheme.typography.body,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(16.dp))
            AuthTextField(
                label = ProfileCopy.DELETE_CONFIRMATION_LABEL,
                value = deletion.confirmationInput,
                onValueChange = onInputChange,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                ),
                placeholder = DeleteAccountUiState.CONFIRMATION_PHRASE,
                enabled = !deletion.isDeleting,
            )
            Spacer(Modifier.height(20.dp))
            StackedDialogActions(
                primaryText = if (deletion.isDeleting) {
                    ProfileCopy.DELETING
                } else {
                    "DELETE MY ACCOUNT"
                },
                onPrimary = onConfirm,
                secondaryText = "CANCEL",
                onSecondary = onDismiss,
                enabled = deletion.canConfirmDeletion,
                // CANCEL is gated on the deletion alone, never on the phrase. Changing one's
                // mind has to stay one tap for as long as nothing has been sent — an empty
                // field greying out the way out would leave a dialog with no visible exit.
                secondaryEnabled = !deletion.isDeleting,
            )
        }
    }
}

/**
 * Nothing was removed, and the first sentence after the title says so.
 *
 * A failed deletion is the one failure in the app where the lifter's first thought will be
 * "what happened to my training?", so the answer comes before the reason and before the
 * retry.
 */
@Composable
private fun DeleteFailureDialog(
    failure: DeleteAccountFailureUi,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GriffGymTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        AccountDialogSurface {
            Text(
                text = ProfileCopy.DELETE_FAILED_TITLE,
                style = GriffGymTheme.typography.headline,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = ProfileCopy.DELETE_FAILED_NOTHING_REMOVED,
                style = GriffGymTheme.typography.body,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (failure) {
                    DeleteAccountFailureUi.RETRYABLE -> ProfileCopy.DELETE_FAILED_TRY_AGAIN
                    DeleteAccountFailureUi.SESSION_EXPIRED -> ProfileCopy.DELETE_FAILED_SIGN_IN
                },
                style = GriffGymTheme.typography.body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            StackedDialogActions(
                primaryText = "TRY AGAIN",
                onPrimary = onRetry,
                secondaryText = "CANCEL",
                onSecondary = onDismiss,
            )
        }
    }
}

/** Sign-out, worded as it is on the account screen: the cloud copy is kept. */
@Composable
private fun ProfileSignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
            StackedDialogActions(
                primaryText = "SIGN OUT",
                onPrimary = onConfirm,
                secondaryText = "BACK",
                onSecondary = onDismiss,
            )
        }
    }
}

/**
 * A caps label over a hairline. The only structure this screen's bands need.
 *
 * The danger zone passes its own [color] for both, so the fence around it is visible before
 * the words under it are read.
 */
@Composable
private fun ProfileSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = GriffGymTheme.colors.textTertiary,
    lineColor: Color = GriffGymTheme.colors.outline,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = GriffGymTheme.typography.label,
            color = color,
        )
        Spacer(Modifier.height(8.dp))
        HairLine(color = lineColor)
    }
}

/** "Status ............ SYNCED" — a label with whatever answers it on the right. */
@Composable
private fun ProfileValueRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GriffGymTheme.dimens.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = GriffGymTheme.typography.body,
            color = GriffGymTheme.colors.textSecondary,
        )
        value()
    }
}

/** A tappable row with a chevron: the drawer's vocabulary, at row scale. */
@Composable
private fun ProfileActionRow(label: String, onClick: () -> Unit) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GriffGymTheme.dimens.touchTarget)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = GriffGymTheme.typography.label,
            color = colors.textPrimary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Every sentence this screen is allowed to say about deletion.
 *
 * Asserted verbatim by the tests, because the wording is the safety guarantee — "nothing has
 * been removed" after a failure is the difference between a lifter retrying calmly and one
 * assuming six years of training just went with a bad connection.
 */
internal object ProfileCopy {

    const val TITLE = "PROFILE"

    const val ACCOUNT_KIND = "GRIFF GYM ACCOUNT"

    const val SECTION_CLOUD_BACKUP = "CLOUD BACKUP"
    const val SECTION_ACCOUNT = "ACCOUNT"
    const val SECTION_DANGER_ZONE = "DANGER ZONE"

    const val DELETE_EXPLANATION = "Deleting your account permanently removes your Griff Gym " +
        "account and all cloud data associated with it."

    const val DELETE_STAGE_ONE_TITLE = "DELETE YOUR ACCOUNT?"

    const val DELETE_STAGE_ONE_BODY = "This removes:"

    val DELETE_REMOVES: List<String> = listOf(
        "your Griff Gym account",
        "your training cycles",
        "your workout history",
        "any active workout",
        "every logged set",
        "your reference maxes",
        "your cloud backup",
    )

    const val DELETE_CANNOT_BE_UNDONE = "This cannot be undone."

    const val DELETE_STAGE_TWO_TITLE = "PERMANENTLY DELETE ACCOUNT"

    const val DELETE_STAGE_TWO_INSTRUCTION = "Type DELETE to confirm."

    const val DELETE_CONFIRMATION_LABEL = "Confirmation"

    const val DELETING = "DELETING ACCOUNT…"

    const val DELETE_FAILED_TITLE = "ACCOUNT COULD NOT BE DELETED"

    const val DELETE_FAILED_NOTHING_REMOVED =
        "Your account and training data have not been removed."

    const val DELETE_FAILED_TRY_AGAIN = "Please check your connection and try again."

    /** The refresh failed too, so no retry can work until there is a session again. */
    const val DELETE_FAILED_SIGN_IN =
        "Please sign in again before your account can be deleted."
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    GriffGymTheme {
        ProfileScreen(
            state = ProfileUiState(
                isLoading = false,
                email = "lifter@griffgym.app",
                status = BackupStatusUi.BACKED_UP,
                lastBackupLabel = "Today, 18:42",
            ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun ProfileScreenConfirmingDeletionPreview() {
    GriffGymTheme {
        ProfileScreen(
            state = ProfileUiState(
                isLoading = false,
                email = "lifter@griffgym.app",
                status = BackupStatusUi.BACKED_UP,
                lastBackupLabel = "Today, 18:42",
                deletion = DeleteAccountUiState(
                    stage = DeleteAccountStage.CONFIRMATION,
                    confirmationInput = "DELETE",
                ),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
