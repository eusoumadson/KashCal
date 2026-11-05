package org.onekash.kashcal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onekash.kashcal.testutil.resolveProjectRoot
import java.io.File

/**
 * Validates the primary README.md documentation according to RF-03, RF-05, AC-03 and AC-05:
 * - Written in Portuguese (PT-BR) under the "Abacate Calendário" identity (launcher label "Calendário").
 * - Highlights the 3 pillars: Calendars/CalDAV, Tasks.org tasks integration, and Travel Time alerts.
 * - Includes functional 1-tap Obtainium redirect link and GitHub Releases links.
 * - Gives open attribution and credit to upstream KashCal (https://github.com/KashCal/KashCal).
 * - Excludes proprietary Google Play and F-Droid official store links.
 * - Excludes the third-party upstream signing certificate.
 * - Verifies that all referenced local image files and docs exist on disk.
 */
class ReadmeValidationTest {

    private val projectRoot: File by lazy { resolveProjectRoot() }
    private val readmeFile: File by lazy { File(projectRoot, "README.md") }

    @Test
    fun `readme exists and is in Portuguese with Abacate Calendario identity`() {
        assertTrue("README.md must exist at project root", readmeFile.exists())
        val text = readmeFile.readText()

        assertTrue("Must mention Abacate Calendário", text.contains("Abacate Calendário"))
        assertTrue("Must mention launcher label 'Calendário'", text.contains("Calendário"))
        assertTrue("Must be primarily in Portuguese", text.contains("Todos os seus calendários") || text.contains("privacidade") || text.contains("produtividade"))
    }

    @Test
    fun `readme presents the three core pillars`() {
        val text = readmeFile.readText()

        // Pillar 1: Calendars / Events / CalDAV / iCloud
        assertTrue("Must cover Calendars & CalDAV sync", text.contains("CalDAV", ignoreCase = true))
        assertTrue("Must cover iCloud sync", text.contains("iCloud", ignoreCase = true))

        // Pillar 2: Tasks.org integration
        assertTrue("Must cover Tasks.org integration", text.contains("Tasks.org", ignoreCase = true))
        assertTrue("Must cover tasks feature", text.contains("tarefas", ignoreCase = true))

        // Pillar 3: Travel Time departure alerts
        assertTrue("Must cover Travel Time departure alerts", text.contains("Travel Time", ignoreCase = true) || text.contains("deslocamento", ignoreCase = true))
    }

    @Test
    fun `readme includes direct Obtainium redirect link and GitHub releases`() {
        val text = readmeFile.readText()

        // Direct 1-tap Obtainium link
        assertTrue(
            "Must include functional Obtainium redirect link",
            text.contains("https://apps.obtainium.imranr.dev/redirect?r=obtainium://")
        )

        // Repository releases link
        assertTrue(
            "Must link to abacate-calendario GitHub Releases",
            text.contains("https://github.com/eusoumadson/abacate-calendario/releases")
        )
    }

    @Test
    fun `readme attributes upstream KashCal and Tasks org`() {
        val text = readmeFile.readText()

        assertTrue(
            "Must include link to upstream KashCal repository",
            text.contains("https://github.com/KashCal/KashCal")
        )
    }

    @Test
    fun `readme does not contain upstream app store links or third party certificates`() {
        val text = readmeFile.readText()

        assertFalse(
            "Must not link to upstream Google Play store app listing",
            text.contains("play.google.com/store/apps/details?id=org.onekash.kashcal")
        )
        assertFalse(
            "Must not contain Google Play badge or direct links",
            text.contains("https://play.google.com")
        )
        assertFalse(
            "Must not link to upstream F-Droid package",
            text.contains("f-droid.org/packages/org.onekash.kashcal")
        )
        assertFalse(
            "Must not contain upstream third party signing certificate",
            text.contains("B0:47:6C:12:88:28:BE:04:7B:64:FE:43:F7:9B:1D:5F:2C:34:60:B0:72:6F:B3:99:33:B1:16:20:D8:95:46:22")
        )
    }

    @Test
    fun `all local markdown image and file references exist`() {
        val text = readmeFile.readText()

        // Match markdown links: [text](path)
        val mdLinkRegex = Regex("""\[.*?\]\(([^)]+)\)""")
        // Match HTML src: src="path"
        val htmlSrcRegex = Regex("""src="([^"]+)"""")

        val allReferences = mutableListOf<String>()
        mdLinkRegex.findAll(text).forEach { allReferences.add(it.groupValues[1]) }
        htmlSrcRegex.findAll(text).forEach { allReferences.add(it.groupValues[1]) }

        for (ref in allReferences) {
            val cleanRef = ref.trim()
            // Ignore external URLs, mailto, anchor links, and obtainium intent deep-links
            if (cleanRef.startsWith("http://") ||
                cleanRef.startsWith("https://") ||
                cleanRef.startsWith("mailto:") ||
                cleanRef.startsWith("#") ||
                cleanRef.startsWith("obtainium://")
            ) {
                continue
            }

            // Strip any query or anchor fragment
            val pathPart = cleanRef.split("#")[0].split("?")[0]
            if (pathPart.isBlank()) continue

            val localFile = File(projectRoot, pathPart)
            assertTrue(
                "Referenced local file '$cleanRef' (resolved to '${localFile.path}') does not exist",
                localFile.exists()
            )
        }
    }
}
