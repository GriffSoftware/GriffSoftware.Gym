package com.griffgym.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.presentation.components.GriffGymBadge
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
internal fun RestoreProgressRoute(
    onCompleted: (AuthFlowResult) -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RestoreProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.completion.collect(onCompleted)
    }

    RestoreProgressScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onGiveUp = onGiveUp,
        modifier = modifier,
    )
}

/**
 * The payoff screen: a new phone turning back into the one with six months of training on
 * it.
 *
 * The failure state deliberately does not offer to "continue anyway". There is nothing to
 * continue into — the whole reason for being here is that this device is empty — so the
 * honest options are to try again or to step back and decide something else.
 */
@Composable
fun RestoreProgressScreen(
    state: RestoreProgressUiState,
    onEvent: (RestoreProgressUiEvent) -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    val margin = GriffGymTheme.dimens.screenMargin
    val failed = state.status == TransferStatus.FAILED

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = margin, vertical = 24.dp),
    ) {
        GriffGymBadge(
            text = if (failed) "NOT RESTORED" else "RESTORING",
            filled = !failed,
            color = colors.primary,
        )

        Spacer(Modifier.height(16.dp))
        AccountHeading(
            title = if (failed) RestoreCopy.FAILED_TITLE else RestoreCopy.TITLE,
            description = if (failed) null else RestoreCopy.DESCRIPTION,
        )

        Spacer(Modifier.height(24.dp))
        if (failed) {
            GriffGymCard(accentBar = colors.error) {
                Text(
                    text = RestoreCopy.FAILED_BODY,
                    style = GriffGymTheme.typography.body,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = RestoreCopy.FAILED_REASSURANCE,
                    style = GriffGymTheme.typography.body,
                    color = colors.textSecondary,
                )
                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    HairLine()
                    Spacer(Modifier.height(12.dp))
                    AccountErrorText(state.error)
                }
            }
        } else {
            GriffGymCard(contentPadding = 0.dp) {
                // Square ends and no gap: Material's default indicator is the one component
                // that would immediately look like a different app. The indeterminate overload
                // has no stop indicator to suppress, so there is nothing else to strip.
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = colors.primary,
                    trackColor = colors.surfaceVariant,
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                )
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = RestoreCopy.WORKING,
                        style = GriffGymTheme.typography.body,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = RestoreCopy.WORKING_DETAIL,
                        style = GriffGymTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        GriffGymPrimaryButton(
            text = if (failed) "TRY AGAIN" else "RESTORING…",
            onClick = { onEvent(RestoreProgressUiEvent.Retry) },
            modifier = Modifier.fillMaxWidth(),
            enabled = failed,
        )

        if (failed) {
            Spacer(Modifier.height(4.dp))
            AccountTertiaryAction(text = "NOT NOW", onClick = onGiveUp)
        }
    }
}

internal object RestoreCopy {

    const val TITLE = "RESTORING YOUR TRAINING DATA"

    const val DESCRIPTION = "Bringing your cycles, workouts and reference maxes back from " +
        "your account."

    const val WORKING = "Rebuilding your training history"

    const val WORKING_DETAIL = "Everything arrives at once, or not at all. Nothing is written " +
        "until the whole backup is here."

    const val FAILED_TITLE = "COULD NOT RESTORE"

    const val FAILED_BODY = "Your account's backup could not be downloaded."

    const val FAILED_REASSURANCE = "Nothing on this device was changed, and nothing in your " +
        "account was lost. You can try again."
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun RestoreProgressScreenPreview() {
    GriffGymTheme {
        RestoreProgressScreen(
            state = RestoreProgressUiState(),
            onEvent = {},
            onGiveUp = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun RestoreProgressScreenFailedPreview() {
    GriffGymTheme {
        RestoreProgressScreen(
            state = RestoreProgressUiState(
                status = TransferStatus.FAILED,
                error = AccountMessages.NO_CONNECTION,
            ),
            onEvent = {},
            onGiveUp = {},
        )
    }
}
