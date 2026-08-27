package com.griffgym.presentation.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * The screen that decides whether a lifter's training history can ever be recovered.
 *
 * These assertions are about the *warning*, not the layout. A lifter who taps CONTINUE LOCALLY
 * has to have been told, in plain words, that uninstalling the app destroys their history —
 * and that is a promise worth a test that fails when somebody softens the copy.
 */
@RunWith(RobolectricTestRunner::class)
class DataProtectionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the loss warning names every way the data can go`() {
        composeRule.setContent {
            GriffGymTheme {
                DataProtectionScreen(
                    state = DataProtectionUiState(),
                    onEvent = {},
                    onCreateAccount = {},
                    onSignIn = {},
                )
            }
        }

        composeRule.onNodeWithText(DataProtectionCopy.HEADLINE).assertIsDisplayed()
        composeRule.onNodeWithText(DataProtectionCopy.CONSEQUENCE).assertIsDisplayed()

        // Uninstall, cleared data and a lost device are three different ways to lose a training
        // history, and a lifter deciding this deserves all three named.
        listOf("uninstall", "clear the app data", "lose this device").forEach { phrase ->
            assertTrue(
                "the warning no longer mentions '$phrase'",
                DataProtectionCopy.CONSEQUENCE.contains(phrase),
            )
        }

        // And it must never imply recovery is possible.
        assertTrue(DataProtectionCopy.CONSEQUENCE.contains("cannot be recovered"))
    }

    @Test
    fun `all three ways forward are offered`() {
        composeRule.setContent {
            GriffGymTheme {
                DataProtectionScreen(
                    state = DataProtectionUiState(),
                    onEvent = {},
                    onCreateAccount = {},
                    onSignIn = {},
                )
            }
        }

        // The screen scrolls, and on a short display the third option starts below the fold.
        // Scrolling to it is part of what is being checked: it has to be reachable, not merely
        // present in the tree.
        listOf("CREATE ACCOUNT", "SIGN IN", "CONTINUE LOCALLY").forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `continuing locally asks for confirmation rather than doing it`() {
        val events = mutableListOf<DataProtectionUiEvent>()

        composeRule.setContent {
            GriffGymTheme {
                DataProtectionScreen(
                    state = DataProtectionUiState(),
                    onEvent = events::add,
                    onCreateAccount = {},
                    onSignIn = {},
                )
            }
        }

        composeRule.onNodeWithText("CONTINUE LOCALLY").performScrollTo().performClick()

        // Requested, not confirmed. The tap opens the warning; it does not settle the decision.
        assertEquals(listOf(DataProtectionUiEvent.ContinueLocallyRequested), events)
    }

    @Test
    fun `the confirmation repeats the consequence and offers a way back`() {
        composeRule.setContent {
            GriffGymTheme {
                DataProtectionScreen(
                    state = DataProtectionUiState(isConfirmingLocalOnly = true),
                    onEvent = {},
                    onCreateAccount = {},
                    onSignIn = {},
                )
            }
        }

        composeRule.onNodeWithText(DataProtectionCopy.CONFIRM_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(DataProtectionCopy.CONFIRM_CONSEQUENCE).assertIsDisplayed()

        // Creating an account is still one tap away at the moment of deciding not to. Matched
        // across all nodes because the screen behind the dialog offers it too — which is the
        // point: the offer does not disappear while the lifter is deciding against it.
        assertTrue(
            "the confirmation no longer offers a way to create an account",
            composeRule.onAllNodesWithText("CREATE ACCOUNT").fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onNodeWithText("I UNDERSTAND — CONTINUE").assertIsDisplayed()
    }

    @Test
    fun `only the explicit confirmation settles it`() {
        val events = mutableListOf<DataProtectionUiEvent>()

        composeRule.setContent {
            GriffGymTheme {
                DataProtectionScreen(
                    state = DataProtectionUiState(isConfirmingLocalOnly = true),
                    onEvent = events::add,
                    onCreateAccount = {},
                    onSignIn = {},
                )
            }
        }

        composeRule.onNodeWithText("I UNDERSTAND — CONTINUE").performClick()

        assertEquals(listOf(DataProtectionUiEvent.ConfirmContinueLocally), events)
    }
}
