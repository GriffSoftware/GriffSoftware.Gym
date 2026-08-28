package com.griffgym.presentation.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.griffgym.presentation.theme.GriffGymTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The deletion dialogs on a screen too short to hold them.
 *
 * Deliberately run at Robolectric's default canvas — 470dp tall — rather than at the roomy
 * qualifier the rest of `ProfileScreenTest` uses. The first dialog lists seven things it is
 * about to destroy, and on a short device or at a large font scale that is enough to push
 * CONTINUE and CANCEL past the bottom edge. `AccountDialogSurface` had no scrolling and no
 * maximum height, so they were simply unreachable.
 *
 * That is not a layout nitpick. It is a lifter held inside a confirmation they cannot answer
 * either way, on the one dialog in the app where being unable to say *no* is the failure that
 * matters. Both buttons must stay reachable however little room there is.
 */
@RunWith(RobolectricTestRunner::class)
class DeleteDialogFitsShortScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<ProfileUiEvent>()

    @Test
    fun `both answers to the first question stay reachable on a short screen`() {
        render(DeleteAccountStage.EXPLANATION)

        composeRule.onNodeWithText("CONTINUE").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("CANCEL").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(
            listOf(
                ProfileUiEvent.DeleteAccountExplained,
                ProfileUiEvent.DismissDeleteAccount,
            ),
            events,
        )
    }

    @Test
    fun `the way out of the second question stays reachable on a short screen`() {
        render(DeleteAccountStage.CONFIRMATION)

        composeRule.onNodeWithText("CANCEL").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(listOf(ProfileUiEvent.DismissDeleteAccount), events)
    }

    private fun render(stage: DeleteAccountStage) {
        composeRule.setContent {
            GriffGymTheme {
                ProfileScreen(
                    state = ProfileUiState(
                        isLoading = false,
                        email = "lifter@griffgym.test",
                        deletion = DeleteAccountUiState(stage = stage),
                    ),
                    onEvent = { events += it },
                    onBack = {},
                )
            }
        }
    }
}
