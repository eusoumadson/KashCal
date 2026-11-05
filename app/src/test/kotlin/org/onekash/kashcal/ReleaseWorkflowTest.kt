package org.onekash.kashcal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onekash.kashcal.testutil.resolveProjectRoot
import java.io.File

/**
 * Validates the GitHub Actions release workflow (.github/workflows/release.yml):
 * - Ensures there is no repository lock condition (`if: github.repository == 'KashCal/KashCal'`)
 *   so the fork pipeline triggers on tags.
 * - Ensures the release asset and artifact naming convention uses `AbacateCalendario-`.
 * - Ensures no legacy `KashCal-` binary references remain in release steps.
 */
class ReleaseWorkflowTest {

    private val workflowFile: File by lazy {
        File(resolveProjectRoot(), ".github/workflows/release.yml")
    }

    @Test
    fun `release workflow file exists`() {
        assertTrue("Workflow file must exist at .github/workflows/release.yml", workflowFile.exists())
    }

    @Test
    fun `release workflow does not restrict execution to upstream repository`() {
        val content = workflowFile.readText()
        assertFalse(
            "Workflow must not contain upstream repo lock condition",
            content.contains("github.repository == 'KashCal/KashCal'")
        )
    }

    @Test
    fun `release workflow publishes APK with AbacateCalendario naming convention`() {
        val content = workflowFile.readText()
        val expectedApkName = "AbacateCalendario-\${{ steps.version.outputs.version }}-release.apk"

        assertTrue(
            "Rename step must rename APK to $expectedApkName",
            content.contains("AbacateCalendario-\${{ steps.version.outputs.version }}-release.apk")
        )

        assertTrue(
            "Release files attribute must attach AbacateCalendario APK",
            content.contains("files: app/build/outputs/apk/release/$expectedApkName")
        )

        assertTrue(
            "Artifact upload name must have AbacateCalendario prefix",
            content.contains("name: AbacateCalendario-\${{ steps.version.outputs.version }}-release")
        )

        assertFalse(
            "Workflow must not reference KashCal- binary names",
            content.contains("KashCal-")
        )
    }
}
