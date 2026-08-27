package com.griffgym.presentation.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.griffgym.presentation.theme.GriffGymTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The screen shown when a phone and an account both hold training history.
 *
 * This is the most destructive thing the app can be asked to do, so what these tests hold in
 * place is the refusal: no automatic merge, no automatic overwrite, and no single tap that
 * replaces months of training. If any of these ever go green-to-red, the answer is not to
 * relax the test.
 */
@RunWith(RobolectricTestRunner::class)
class DataConflictScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the app says outright that it will not merge or overwrite on its own`() {
        render(DataConflictUiState())

        composeRule.onNodeWithText(DataConflictCopy.TITLE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(DataConflictCopy.BODY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(DataConflictCopy.POLICY).performScrollTo().assertIsDisplayed()

        assertTrue(DataConflictCopy.POLICY.contains("will not merge"))
        assertTrue(DataConflictCopy.POLICY.contains("automatically"))
    }

    @Test
    fun `both options are offered, and cancelling changes nothing`() {
        render(DataConflictUiState())

        composeRule.onNodeWithText("USE CLOUD DATA").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("CANCEL").performScrollTo().assertIsDisplayed()

        // The screen states the consequence of each before either is chosen.
        composeRule.onNodeWithText(DataConflictCopy.CHOICE).performScrollTo().assertIsDisplayed()
        assertTrue(DataConflictCopy.CHOICE.contains("leaves both copies exactly as they are"))
    }

    @Test
    fun `using the cloud copy asks a second time before replacing anything`() {
        val events = mutableListOf<DataConflictUiEvent>()

        render(DataConflictUiState(), onEvent = events::add)

        composeRule.onNodeWithText("USE CLOUD DATA").performScrollTo().performClick()

        // Requested, not confirmed. One tap must not be able to replace a training history.
        assertEquals(listOf(DataConflictUiEvent.UseCloudRequested), events)
    }

    @Test
    fun `the confirmation spells out that this cannot be undone`() {
        render(DataConflictUiState(isConfirmingUseCloud = true))

        composeRule.onNodeWithText(DataConflictCopy.CONFIRM_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(DataConflictCopy.CONFIRM_BODY).assertIsDisplayed()
        composeRule.onNodeWithText(DataConflictCopy.CONFIRM_CONSEQUENCE).assertIsDisplayed()

        // And backing out is still the easier of the two.
        composeRule.onNodeWithText("KEEP BOTH FOR NOW").assertIsDisplayed()
        composeRule.onNodeWithText("REPLACE LOCAL DATA").assertIsDisplayed()
    }

    @Test
    fun `only the second, explicit confirmation replaces the local copy`() {
        val events = mutableListOf<DataConflictUiEvent>()

        render(DataConflictUiState(isConfirmingUseCloud = true), onEvent = events::add)

        composeRule.onNodeWithText("REPLACE LOCAL DATA").performClick()

        assertEquals(listOf(DataConflictUiEvent.ConfirmUseCloud), events)
    }

    private fun render(
        state: DataConflictUiState,
        onEvent: (DataConflictUiEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            GriffGymTheme {
                DataConflictScreen(
                    state = state,
                    onEvent = onEvent,
                    onUseCloudData = {},
                    onCancel = {},
                )
            }
        }
    }
}
