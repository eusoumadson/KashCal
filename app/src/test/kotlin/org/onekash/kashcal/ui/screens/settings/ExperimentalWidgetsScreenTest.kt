package org.onekash.kashcal.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Compose tests for [ExperimentalWidgetsScreen].
 *
 * Verifies:
 * - Single unified SplitAgendaWidget card is displayed.
 * - Legacy MonthAgendaWidget card is absent.
 * - Master switch toggling fires [onToggleEnabled].
 * - TopAppBar navigate back fires [onNavigateBack].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], qualifiers = "w360dp-h9999dp-mdpi")
class ExperimentalWidgetsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun pinLocale() = Locale.setDefault(Locale.US)

    @Test
    fun `displays unified split agenda card and no month agenda card`() {
        composeTestRule.setContent {
            MaterialTheme {
                ExperimentalWidgetsScreen(
                    isEnabled = true,
                    onToggleEnabled = {},
                    onNavigateBack = {}
                )
            }
        }

        // Split agenda widget card is displayed
        composeTestRule.onNodeWithText("Split Agenda").assertIsDisplayed()

        // Legacy Month+Agenda widget card is completely absent
        composeTestRule.onNode(hasText("Month + Agenda")).assertDoesNotExist()
    }

    @Test
    fun `toggle master switch invokes callback`() {
        var toggledValue: Boolean? = null

        composeTestRule.setContent {
            MaterialTheme {
                ExperimentalWidgetsScreen(
                    isEnabled = false,
                    onToggleEnabled = { toggledValue = it },
                    onNavigateBack = {}
                )
            }
        }

        // Click the Switch
        composeTestRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .performClick()
        assertEquals(true, toggledValue)
    }

    @Test
    fun `navigation back invokes callback`() {
        var backClicked = false

        composeTestRule.setContent {
            MaterialTheme {
                ExperimentalWidgetsScreen(
                    isEnabled = true,
                    onToggleEnabled = {},
                    onNavigateBack = { backClicked = true }
                )
            }
        }

        // Back button in SettingsTopAppBar uses R.string.cd_back ("Back")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backClicked)
    }
}
