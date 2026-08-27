package com.griffgym.presentation.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
 * The account screen in both of the app's two modes, and the two moments it has to be careful:
 * telling a local-only lifter the truth about their data, and making sure signing out cannot be
 * mistaken for deleting an account.
 */
@RunWith(RobolectricTestRunner::class)
class AccountScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a local lifter is told plainly that nothing is backed up`() {
        render(AccountUiState(isLoading = false, mode = AccountMode.LOCAL_ONLY))

        composeRule.onNodeWithText("LOCAL ONLY").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BackupStatusUi.NOT_BACKED_UP.label)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(AccountCopy.LOCAL_ONLY_EXPLANATION)
            .performScrollTo()
            .assertIsDisplayed()

        // Both ways to fix it are on the screen, not buried behind a menu.
        composeRule.onNode(hasText("CREATE ACCOUNT") and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNode(hasText("SIGN IN") and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `a signed-in lifter sees their email, their status and when it last landed`() {
        render(
            AccountUiState(
                isLoading = false,
                mode = AccountMode.AUTHENTICATED,
                email = "lifter@example.com",
                status = BackupStatusUi.BACKED_UP,
                lastSyncLabel = "Today, 18:42",
            ),
        )

        composeRule.onNodeWithText("lifter@example.com").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BackupStatusUi.BACKED_UP.label)
            .performScrollTo()
            .assertIsDisplayed()
        // From a sync that actually finished — never from one that merely started.
        composeRule.onNodeWithText("Last sync — Today, 18:42")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `being offline is explained, not reported as a fault`() {
        render(
            AccountUiState(
                isLoading = false,
                mode = AccountMode.AUTHENTICATED,
                email = "lifter@example.com",
                status = BackupStatusUi.OFFLINE,
            ),
        )

        composeRule.onNodeWithText(AccountCopy.OFFLINE_EXPLANATION)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `signing out asks first, and says the cloud copy is kept`() {
        render(
            AccountUiState(
                isLoading = false,
                mode = AccountMode.AUTHENTICATED,
                email = "lifter@example.com",
                isConfirmingSignOut = true,
            ),
        )

        composeRule.onNodeWithText(AccountCopy.SIGN_OUT_TITLE).assertIsDisplayed()

        // The single most important sentence on this screen: signing out is not deleting an
        // account, and a lifter must never have to guess about that.
        composeRule.onNodeWithText(AccountCopy.SIGN_OUT_KEEPS_CLOUD).assertIsDisplayed()
        assertTrue(AccountCopy.SIGN_OUT_KEEPS_CLOUD.contains("does not delete"))
        composeRule.onNodeWithText(AccountCopy.SIGN_OUT_LOCAL_EFFECT).assertIsDisplayed()
    }

    @Test
    fun `signing out is not something one tap can do`() {
        val events = mutableListOf<AccountUiEvent>()

        render(
            state = AccountUiState(
                isLoading = false,
                mode = AccountMode.AUTHENTICATED,
                email = "lifter@example.com",
            ),
            onEvent = events::add,
        )

        composeRule.onNode(hasText("SIGN OUT") and hasClickAction())
            .performScrollTo()
            .performClick()

        assertEquals(listOf(AccountUiEvent.SignOutRequested), events)
    }

    @Test
    fun `nothing technical is ever shown to the lifter`() {
        render(
            AccountUiState(
                isLoading = false,
                mode = AccountMode.AUTHENTICATED,
                email = "lifter@example.com",
                status = BackupStatusUi.FAILED,
                message = "The last backup did not finish.",
            ),
        )

        listOf("Room", "JWT", "Retrofit", "HTTP", "Worker", "DTO", "SQL").forEach { jargon ->
            assertEquals(
                "'$jargon' leaked into the account screen",
                0,
                composeRule.onAllNodesWithText(jargon, substring = true)
                    .fetchSemanticsNodes().size,
            )
        }
    }

    private fun render(
        state: AccountUiState,
        onEvent: (AccountUiEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            GriffGymTheme {
                AccountScreen(
                    state = state,
                    onEvent = onEvent,
                    onCreateAccount = {},
                    onSignIn = {},
                )
            }
        }
    }
}
