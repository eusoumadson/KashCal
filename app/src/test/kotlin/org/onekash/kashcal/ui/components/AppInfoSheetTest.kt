package org.onekash.kashcal.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.onekash.kashcal.ui.screens.settings.VersionFooter
import org.onekash.kashcal.util.ExternalLinks
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h800dp-mdpi")
class AppInfoSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val opened = mutableListOf<String>()

    @Test
    fun `VersionFooter renders Abacate Calendario brand and installed version`() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                VersionFooter(
                    versionName = "2026.11.06-1",
                    onClick = { clicked = true },
                )
            }
        }

        val footerNode = composeTestRule.onNodeWithText("Abacate Calendário 🥑 v2026.11.06-1")
        footerNode.assertExists()
        footerNode.assertIsDisplayed()
        footerNode.performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun `AppInfoSheet displays brand heading, taglines, and fork and upstream links`() {
        composeTestRule.setContent {
            MaterialTheme {
                AppInfoSheet(
                    onDismiss = {},
                    onOpenLink = { url -> opened += url; true },
                )
            }
        }

        composeTestRule.onNode(hasText("Abacate Calendário 🥑") and isHeading()).assertExists()
        composeTestRule.onNodeWithText("Privacy, productivity, and your calendars.").assertExists()
        composeTestRule.onNodeWithText("For you, not your data.").assertExists()
        composeTestRule.onNodeWithText("github.com/eusoumadson/abacate-calendario").assertExists()
        composeTestRule.onNodeWithText("Based on KashCal").assertExists()
        composeTestRule.onNodeWithText("Keep it that way").assertExists()
        composeTestRule.onNodeWithText("kashcal.onekash.org").assertExists()
    }

    @Test
    fun `AppInfoSheet triggers fork source, upstream donate, and upstream home links`() {
        composeTestRule.setContent {
            MaterialTheme {
                AppInfoSheet(
                    onDismiss = {},
                    onOpenLink = { url -> opened += url; true },
                )
            }
        }

        composeTestRule.onNodeWithText("github.com/eusoumadson/abacate-calendario").performClick()
        composeTestRule.onNodeWithText("Keep it that way").performClick()
        composeTestRule.onNodeWithText("kashcal.onekash.org").performClick()

        assertEquals(
            listOf(
                ExternalLinks.FORK_SOURCE,
                ExternalLinks.DONATE,
                ExternalLinks.HOME,
            ),
            opened,
        )
    }

    @Test
    fun `interactive links have button semantics for accessibility`() {
        composeTestRule.setContent {
            MaterialTheme {
                AppInfoSheet(
                    onDismiss = {},
                    onOpenLink = { true },
                )
            }
        }

        composeTestRule.onNode(
            hasText("github.com/eusoumadson/abacate-calendario") and
                hasClickAction() and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).assertExists()

        composeTestRule.onNode(
            hasText("kashcal.onekash.org") and
                hasClickAction() and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).assertExists()
    }
}
