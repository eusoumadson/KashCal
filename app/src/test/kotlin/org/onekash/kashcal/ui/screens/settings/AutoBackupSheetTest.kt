package org.onekash.kashcal.ui.screens.settings

import android.net.Uri
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.onekash.kashcal.ui.theme.KashCalTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], qualifiers = "w360dp-h2000dp-mdpi")
class AutoBackupSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var originalLocale: Locale? = null

    @Before
    fun pinLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        originalLocale?.let { Locale.setDefault(it) }
    }

    private class Calls {
        var toggled: Boolean? = null
        var selectedFolder: Uri? = null
        var selectedInterval: Int? = null
        var selectedRetention: Int? = null
        var dismissed: Int = 0
    }

    private fun render(
        enabled: Boolean = true,
        treeUriString: String? = null,
        intervalDays: Int = 7,
        retentionCount: Int = 5,
        lastRunMs: Long = 0L,
        calls: Calls = Calls(),
    ): Calls {
        composeTestRule.setContent {
            KashCalTheme {
                AutoBackupSheet(
                    enabled = enabled,
                    treeUriString = treeUriString,
                    intervalDays = intervalDays,
                    retentionCount = retentionCount,
                    lastRunMs = lastRunMs,
                    onToggleEnabled = { calls.toggled = it },
                    onFolderSelected = { calls.selectedFolder = it },
                    onIntervalSelected = { calls.selectedInterval = it },
                    onRetentionSelected = { calls.selectedRetention = it },
                    onDismiss = { calls.dismissed++ },
                )
            }
        }
        return calls
    }

    @Test
    fun `renders header description and fork features note`() {
        render()
        composeTestRule.onNodeWithText("Automatic Backup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Periodically export a backup JSON containing all settings and fork configurations to a chosen folder.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings backup includes Abacate Calendário options and advanced fork features (Tasks.org, Travel Time, and widgets).").assertIsDisplayed()
    }

    @Test
    fun `when enabled is false options are hidden and toggle enables it`() {
        val calls = render(enabled = false)
        composeTestRule.onNodeWithText("Disabled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Backup folder").assertDoesNotExist()
        composeTestRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performClick()
        assertEquals(true, calls.toggled)
    }

    @Test
    fun `when enabled is true folder frequency and retention options are shown`() {
        render(enabled = true, treeUriString = "content://com.android.externalstorage.documents/tree/primary%3ABackups")
        composeTestRule.onNodeWithText("Active").assertIsDisplayed()
        composeTestRule.onNodeWithText("Backup folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Backup frequency").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retention (keep latest)").assertIsDisplayed()
    }

    @Test
    fun `frequency filter chip click fires onIntervalSelected`() {
        val calls = render(enabled = true, intervalDays = 7)
        composeTestRule.onNodeWithText("Daily").performClick()
        assertEquals(1, calls.selectedInterval)
    }

    @Test
    fun `retention filter chip click fires onRetentionSelected`() {
        val calls = render(enabled = true, retentionCount = 5)
        composeTestRule.onNodeWithText("10 backups").performClick()
        assertEquals(10, calls.selectedRetention)
    }

    @Test
    fun `renders last backup run time when lastRunMs is set`() {
        render(enabled = true, lastRunMs = System.currentTimeMillis() - 60000L)
        composeTestRule.onNodeWithText("Last backup:", substring = true).assertIsDisplayed()
    }
}
