package com.griffgym.presentation.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * The small line on Home that answers "is my training safe?".
 *
 * Deliberately a card and never a dialog. A local-only lifter made a considered choice and
 * was warned once, clearly, at the time; nagging them with a modal on every launch would be
 * badgering somebody for a decision they already made. It has to be visible enough to notice
 * on the day they change their mind, and quiet enough to ignore on the four hundred days
 * they do not.
 *
 * Self-contained — its own ViewModel, no state threaded through Home — so that adding it did
 * not require Home's state to grow a cloud concern it otherwise has nothing to do with.
 */
@Composable
fun CloudStatusCardRoute(
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Nothing at all until the mode is known. A card that flashes "NOT BACKED UP" for two
    // frames on the launch of a signed-in lifter is worse than no card.
    if (state.isLoading) return

    CloudStatusCard(
        mode = state.mode,
        status = state.status,
        onOpenAccount = onOpenAccount,
        modifier = modifier,
    )
}

@Composable
fun CloudStatusCard(
    mode: AccountMode,
    status: BackupStatusUi,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors

    // Once everything is backed up there is nothing worth saying. The absence of the card is
    // the reassurance; a permanent green tick is just clutter on the screen a lifter opens
    // most often.
    if (mode == AccountMode.AUTHENTICATED && status.isReassuring) return

    GriffGymCard(
        modifier = modifier.fillMaxWidth(),
        accentBar = if (mode == AccountMode.LOCAL_ONLY) colors.primary else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (mode == AccountMode.LOCAL_ONLY) LOCAL_TITLE else CLOUD_TITLE,
                style = GriffGymTheme.typography.title,
                color = colors.textPrimary,
            )
            Text(
                text = status.label,
                style = GriffGymTheme.typography.label,
                color = if (status.isReassuring) colors.textSecondary else colors.primary,
                textAlign = TextAlign.End,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = when {
                mode == AccountMode.LOCAL_ONLY -> LOCAL_BODY
                status == BackupStatusUi.OFFLINE -> OFFLINE_BODY
                status == BackupStatusUi.NEEDS_ATTENTION -> ATTENTION_BODY
                status == BackupStatusUi.FAILED -> FAILED_BODY
                else -> PENDING_BODY
            },
            style = GriffGymTheme.typography.body,
            color = colors.textSecondary,
        )

        if (mode == AccountMode.LOCAL_ONLY) {
            Spacer(Modifier.height(16.dp))
            GriffGymPrimaryButton(
                text = "BACK UP MY DATA",
                onClick = onOpenAccount,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val LOCAL_TITLE = "YOUR DATA IS NOT BACKED UP"
private const val CLOUD_TITLE = "CLOUD BACKUP"

private const val LOCAL_BODY =
    "Your training history is stored only on this device. Create an account to protect it."

private const val OFFLINE_BODY =
    "Changes will be backed up when your connection returns."

private const val PENDING_BODY =
    "Some recent training has not reached your account yet."

private const val FAILED_BODY =
    "The last backup did not finish. Your data is still safe on this device."

private const val ATTENTION_BODY =
    "Some training was changed in two places. Nothing has been lost — open Account to review."

@Preview(widthDp = 390, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun CloudStatusCardLocalPreview() {
    GriffGymTheme {
        CloudStatusCard(
            mode = AccountMode.LOCAL_ONLY,
            status = BackupStatusUi.NOT_BACKED_UP,
            onOpenAccount = {},
        )
    }
}

@Preview(widthDp = 390, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun CloudStatusCardOfflinePreview() {
    GriffGymTheme {
        CloudStatusCard(
            mode = AccountMode.AUTHENTICATED,
            status = BackupStatusUi.OFFLINE,
            onOpenAccount = {},
        )
    }
}
