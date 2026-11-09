package com.example

import com.example.data.database.FindingEntity
import com.example.data.database.InvestigationEntity
import com.example.data.repository.InvestigationRepository
import com.example.data.scanners.EmailOsintEngine
import com.example.data.scanners.PhoneInfogaClient
import com.example.data.scanners.PhoneInfogaScanResult
import com.example.data.scanners.PhoneOsintEngine
import com.example.data.scanners.SpiderFootClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun phoneParsing_validatesUSTollFree() {
        val result = PhoneOsintEngine.parse("+18005550199")
        assertEquals("+1", result.countryCode)
        assertEquals("US", result.isoCode)
        assertTrue(result.isValidFormat)
        assertTrue(result.isTollFree)
        assertTrue(result.lineType.contains("Toll-Free"))
    }

    @Test
    fun phoneParsing_validatesIndianMobileWithMnpDisclaimer() {
        val result = PhoneOsintEngine.parse("+919876543210")
        assertEquals("+91", result.countryCode)
        assertEquals("IN", result.isoCode)
        assertTrue(result.isValidFormat)
        assertFalse(result.isTollFree)
        assertTrue(result.lineType.contains("Mobile"))
        // Confirms Mobile Number Portability (MNP) notice is present
        assertTrue(result.mnpNotice.contains("MNP"))
    }

    @Test
    fun phoneParsing_handlesInvalidNumbers() {
        val tooShort = PhoneOsintEngine.parse("+123")
        assertFalse(tooShort.isValidFormat)
        assertEquals("Invalid / Incomplete", tooShort.lineType)

        val invalidChars = PhoneOsintEngine.parse("abc-123")
        assertFalse(invalidChars.isValidFormat)
    }

    @Test
    fun phoneInfoga_parsesSuccessfulOutput() {
        val sampleJson = """
        {
            "version": "v2.10.0",
            "carrier": "T-Mobile USA",
            "line_type": "mobile",
            "country_name": "United States",
            "location": "California",
            "scanners": [
                {
                    "name": "numverify",
                    "status": "success",
                    "data": { "valid": true, "carrier": "T-Mobile" }
                }
            ]
        }
        """.trimIndent()

        val parsed = PhoneInfogaClient.parsePhoneInfogaJson("INV-TEST-01", sampleJson)
        assertTrue(parsed is PhoneInfogaScanResult.Success)

        val success = parsed as PhoneInfogaScanResult.Success
        assertEquals("T-Mobile USA", success.carrier)
        assertEquals("mobile", success.lineType)
        assertEquals("United States", success.country)
        assertEquals(3, success.findings.size)
    }

    @Test
    fun phoneInfoga_handlesMalformedOutput() {
        val malformed = "{ this is not valid json: 123 }"
        val result = PhoneInfogaClient.parsePhoneInfogaJson("INV-TEST-02", malformed)
        assertTrue(result is PhoneInfogaScanResult.Error)
        val error = result as PhoneInfogaScanResult.Error
        assertTrue(error.message.contains("Failed to parse"))
    }

    @Test
    fun phoneInfoga_detectsBinaryUnavailable() {
        val (available, msg) = PhoneInfogaClient.checkBinaryAvailability("/non/existent/path/phoneinfoga_bin")
        assertFalse(available)
        assertTrue(msg.contains("not found"))
    }

    @Test
    fun spiderFoot_mapsTargetTypesCorrectly() {
        assertEquals("IP_ADDRESS", SpiderFootClient.mapTargetType("IP"))
        assertEquals("DOMAIN_NAME", SpiderFootClient.mapTargetType("DOMAIN"))
        assertEquals("INTERNET_NAME", SpiderFootClient.mapTargetType("HOSTNAME"))
        assertEquals("PHONE_NUMBER", SpiderFootClient.mapTargetType("PHONE"))
        assertEquals("EMAILADDR", SpiderFootClient.mapTargetType("EMAIL"))
        assertEquals("HUMAN_NAME", SpiderFootClient.mapTargetType("USERNAME"))
    }

    @Test
    fun spiderFoot_detectsOfflineDaemonGracefully() = runBlocking {
        // Query an unreachable local port to test offline detection
        val (isOnline, statusMsg) = SpiderFootClient.checkDaemonHealth("http://127.0.0.1:59999")
        assertFalse(isOnline)
        assertTrue(statusMsg.contains("Offline") || statusMsg.contains("Unreachable"))
    }

    @Test
    fun emailOsint_detectsDisposableService() {
        val analysis = EmailOsintEngine.analyze("fraud@mailinator.com")
        assertTrue(analysis.isValidFormat)
        assertTrue(analysis.isDisposable)
        assertFalse(analysis.isFreeWebmail)
    }

    @Test
    fun emailOsint_detectsInvalidSyntax() {
        val invalid = EmailOsintEngine.analyze("plainaddress_without_at")
        assertFalse(invalid.isValidFormat)
        assertEquals("", invalid.domain)
    }

    @Test
    fun reportGeneration_outputsValidJsonAndCsv() {
        val inv = InvestigationEntity(
            id = "INV-RPT-01",
            target = "+18005550199",
            type = "PHONE",
            status = "COMPLETED",
            scanner = "PhoneInfoga & ITU-T",
            summary = "Sample summary report",
            riskScore = 15,
            riskLevel = "LOW",
            findingsCount = 2,
            timestamp = 1700000000000L,
            durationMs = 500
        )

        val findings = listOf(
            FindingEntity(
                id = 1,
                investigationId = "INV-RPT-01",
                category = "METADATA",
                source = "ITU-T E.164",
                title = "Format Check",
                description = "Valid toll-free line.",
                confidence = "LOCAL_VALIDATION",
                severity = "INFO"
            ),
            FindingEntity(
                id = 2,
                investigationId = "INV-RPT-01",
                category = "REFERENCE_QUERY",
                source = "Google Web Index",
                title = "Search Query",
                description = "Query generated for verification.",
                url = "https://www.google.com/search?q=\"18005550199\"",
                confidence = "REFERENCE_QUERY",
                severity = "INFO"
            )
        )

        // Call companion object static report generation methods directly without Android context
        val jsonReport = InvestigationRepository.generateJsonReport(inv, findings)
        assertTrue(jsonReport.contains("\"investigation_id\": \"INV-RPT-01\""))
        assertTrue(jsonReport.contains("\"target\":"))
        assertTrue(jsonReport.contains("\"findings_count\": 2"))

        val csvReport = InvestigationRepository.generateCsvReport(inv, findings)
        assertTrue(csvReport.startsWith("InvestigationID,Target,Type"))
        assertTrue(csvReport.contains("\"INV-RPT-01\",\"+18005550199\""))

        val textReport = InvestigationRepository.generateTextReport(inv, findings)
        assertTrue(textReport.contains("NUMBERSCOPE OSINT INVESTIGATION REPORT"))
        assertTrue(textReport.contains("ETHICAL DISCLAIMER & LIMITATIONS"))
    }
}
