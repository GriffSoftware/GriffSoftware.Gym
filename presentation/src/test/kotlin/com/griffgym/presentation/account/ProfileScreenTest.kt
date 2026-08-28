package com.griffgym.presentation.account

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.robolectric.annotation.Config

/**
 * The profile screen, and the four states of the one action on it that cannot be undone.
 *
 * The copy is asserted verbatim on purpose. After a failed deletion, "your account and
 * training data have not been removed" is not decoration — it is the difference between a
 * lifter retrying calmly and one believing six years of training went with a bad
 * connection.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric's default canvas is 470dp tall, which no phone made this decade is, and the
// first deletion dialog — seven bullet points and two stacked buttons — does not fit on it.
// Only the height is raised; `+` leaves the rest of the default configuration alone.
@Config(qualifiers = "+h800dp")
class ProfileScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Hoisted so a test can re-render the same screen with a different state: the rule
     * allows `setContent` exactly once, and several of these cases are about the difference
     * between two states rather than about one of them.
     */
    private val hostedState = mutableStateOf(ProfileUiState())
    private var isMounted = false

    private val events = mutableListOf<ProfileUiEvent>()
    private var backs = 0

    // ---------------------------------------------------------------- the screen itself

    @Test
    fun `the screen says who is signed in and whether their training is safe`() {
        render(
            ProfileUiState(
                isLoading = false,
                email = "lifter@griffgym.test",
                status = BackupStatusUi.BACKED_UP,
                lastBackupLabel = "Today, 18:42",
            ),
        )

        composeRule.onNodeWithText("lifter@griffgym.test").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(ProfileCopy.ACCOUNT_KIND).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(ProfileCopy.SECTION_CLOUD_BACKUP)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(BackupStatusUi.BACKED_UP.label)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNode(hasText("SYNC NOW") and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNode(hasText("SIGN OUT") and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** Absent rather than "never": a first sync that has not landed yet is not a fault. */
    @Test
    fun `the last backup row appears only once something has actually been backed up`() {
        render(ProfileUiState(isLoading = false, email = "lifter@griffgym.test"))

        composeRule.onAllNodesWithText("Last backup").assertCountEquals(0)

        render(
            ProfileUiState(
                isLoading = false,
                email = "lifter@griffgym.test",
                lastBackupLabel = "Yesterday, 07:15",
            ),
        )

        composeRule.onNodeWithText("Last backup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Yesterday, 07:15").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the danger zone is fenced off and says what it costs`() {
        render(ProfileUiState(isLoading = false, email = "lifter@griffgym.test"))

        composeRule.onNodeWithText(ProfileCopy.SECTION_DANGER_ZONE)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(ProfileCopy.DELETE_EXPLANATION)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNode(hasText("DELETE ACCOUNT") and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** One tap opens a question. It can never be the tap that deletes anything. */
    @Test
    fun `tapping delete account only ever opens the first question`() {
        render(ProfileUiState(isLoading = false, email = "lifter@griffgym.test"))

        composeRule.onNode(hasText("DELETE ACCOUNT") and hasClickAction())
            .performScrollTo()
            .performClick()

        assertEquals(listOf(ProfileUiEvent.DeleteAccountRequested), events)
    }

    @Test
    fun `back leaves the screen`() {
        render(ProfileUiState(isLoading = false, email = "lifter@griffgym.test"))

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
    }

    // ---------------------------------------------------------------- stage one

    @Test
    fun `the first question lists everything that will be removed`() {
        render(stagedAt(DeleteAccountStage.EXPLANATION))

        composeRule.onNodeWithText(ProfileCopy.DELETE_STAGE_ONE_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(ProfileCopy.DELETE_STAGE_ONE_BODY).assertIsDisplayed()
        ProfileCopy.DELETE_REMOVES.forEach { item ->
            composeRule.onNodeWithText(item).assertIsDisplayed()
        }
        composeRule.onNodeWithText(ProfileCopy.DELETE_CANNOT_BE_UNDONE).assertIsDisplayed()

        composeRule.onNode(hasText("CONTINUE") and hasClickAction()).assertIsDisplayed()
        composeRule.onNode(hasText("CANCEL") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun `continuing past the first question asks the second and deletes nothing`() {
        render(stagedAt(DeleteAccountStage.EXPLANATION))

        composeRule.onNode(hasText("CONTINUE") and hasClickAction()).performClick()

        assertEquals(listOf(ProfileUiEvent.DeleteAccountExplained), events)
    }

    // ---------------------------------------------------------------- stage two

    @Test
    fun `the second question asks for the phrase and refuses an empty field`() {
        render(stagedAt(DeleteAccountStage.CONFIRMATION))

        composeRule.onNodeWithText(ProfileCopy.DELETE_STAGE_TWO_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(ProfileCopy.DELETE_STAGE_TWO_INSTRUCTION).assertIsDisplayed()
        confirmButton().assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun `a phrase that is not the phrase leaves the button dead`() {
        listOf("delete", "Delete", "DELETEE", "DEL").forEach { typed ->
            render(stagedAt(DeleteAccountStage.CONFIRMATION, confirmationInput = typed))
            confirmButton().assertIsNotEnabled()
        }
    }

    @Test
    fun `the phrase, typed as asked, arms the button`() {
        render(stagedAt(DeleteAccountStage.CONFIRMATION, confirmationInput = "DELETE"))

        confirmButton().assertIsEnabled().performClick()

        assertEquals(listOf(ProfileUiEvent.ConfirmDeleteAccount), events)
    }

    /**
     * Changing one's mind must stay possible for as long as nothing has been sent. A CANCEL
     * greyed out because the field below it is empty reads as a dialog with no way out.
     */
    @Test
    fun `cancelling the second question is possible before anything is sent`() {
        render(stagedAt(DeleteAccountStage.CONFIRMATION))

        cancelButton().assertIsEnabled().performClick()

        assertEquals(listOf(ProfileUiEvent.DismissDeleteAccount), events)
    }

    @Test
    fun `while the deletion is running the dialog says so and takes no more taps`() {
        render(
            stagedAt(
                DeleteAccountStage.CONFIRMATION,
                confirmationInput = "DELETE",
                isDeleting = true,
            ),
        )

        composeRule.onNodeWithText(ProfileCopy.DELETING).assertIsDisplayed()
        composeRule.onNode(hasText(ProfileCopy.DELETING) and hasClickAction())
            .assertIsNotEnabled()
        // The call is already at the server, so cancelling it is not on offer either.
        cancelButton().assertIsNotEnabled()
    }

    // ---------------------------------------------------------------- the failure

    @Test
    fun `a failed deletion says first that nothing was removed`() {
        render(
            stagedAt(
                DeleteAccountStage.FAILURE,
                confirmationInput = "DELETE",
                failure = DeleteAccountFailureUi.RETRYABLE,
            ),
        )

        composeRule.onNodeWithText(ProfileCopy.DELETE_FAILED_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(ProfileCopy.DELETE_FAILED_NOTHING_REMOVED).assertIsDisplayed()
        assertTrue(ProfileCopy.DELETE_FAILED_NOTHING_REMOVED.contains("have not been removed"))
        composeRule.onNodeWithText(ProfileCopy.DELETE_FAILED_TRY_AGAIN).assertIsDisplayed()

        composeRule.onNode(hasText("TRY AGAIN") and hasClickAction())
            .assertIsDisplayed()
            .assertIsEnabled()
        composeRule.onNode(hasText("CANCEL") and hasClickAction()).assertIsDisplayed()
    }

    /** Retrying cannot help without a session, so the dialog asks for one instead. */
    @Test
    fun `an expired session is explained rather than blamed on the connection`() {
        render(
            stagedAt(
                DeleteAccountStage.FAILURE,
                failure = DeleteAccountFailureUi.SESSION_EXPIRED,
            ),
        )

        composeRule.onNodeWithText(ProfileCopy.DELETE_FAILED_SIGN_IN).assertIsDisplayed()
        composeRule.onAllNodesWithText(ProfileCopy.DELETE_FAILED_TRY_AGAIN).assertCountEquals(0)
    }

    @Test
    fun `trying again is an ordinary second attempt, not a new dialog`() {
        render(
            stagedAt(
                DeleteAccountStage.FAILURE,
                failure = DeleteAccountFailureUi.RETRYABLE,
            ),
        )

        composeRule.onNode(hasText("TRY AGAIN") and hasClickAction()).performClick()

        assertEquals(listOf(ProfileUiEvent.RetryDeleteAccount), events)
    }

    // ---------------------------------------------------------------- helpers

    private fun confirmButton() =
        composeRule.onNode(hasText("DELETE MY ACCOUNT") and hasClickAction())

    private fun cancelButton() = composeRule.onNode(hasText("CANCEL") and hasClickAction())

    private fun stagedAt(
        stage: DeleteAccountStage,
        confirmationInput: String = "",
        isDeleting: Boolean = false,
        failure: DeleteAccountFailureUi? = null,
    ) = ProfileUiState(
        isLoading = false,
        email = "lifter@griffgym.test",
        status = BackupStatusUi.BACKED_UP,
        deletion = DeleteAccountUiState(
            stage = stage,
            confirmationInput = confirmationInput,
            isDeleting = isDeleting,
            failure = failure,
        ),
    )

    /** Mounts the screen on the first call and re-renders it on any later one. */
    private fun render(state: ProfileUiState) {
        hostedState.value = state
        if (isMounted) return

        isMounted = true
        composeRule.setContent {
            GriffGymTheme {
                ProfileScreen(
                    state = hostedState.value,
                    onEvent = events::add,
                    onBack = { backs++ },
                )
            }
        }
    }
}
