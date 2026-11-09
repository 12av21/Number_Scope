package com.example.data.repository

import com.example.data.database.AppDatabase
import com.example.data.database.AuditLogEntity
import com.example.data.database.FindingEntity
import com.example.data.database.InvestigationEntity
import com.example.data.database.ScamReportEntity
import com.example.data.scanners.EmailOsintEngine
import com.example.data.scanners.PhoneOsintEngine
import com.example.data.scanners.SpiderFootEngine
import com.example.data.scanners.UsernameOsintEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class InvestigationRepository(private val db: AppDatabase) {

    val allInvestigations: Flow<List<InvestigationEntity>> = db.investigationDao().getAllInvestigations()
    val recentInvestigations: Flow<List<InvestigationEntity>> = db.investigationDao().getRecentInvestigations(10)
    val totalCount: Flow<Int> = db.investigationDao().getTotalCount()
    val completedCount: Flow<Int> = db.investigationDao().getCompletedCount()
    val runningCount: Flow<Int> = db.investigationDao().getRunningCount()
    val failedCount: Flow<Int> = db.investigationDao().getFailedCount()

    val allScamReports: Flow<List<ScamReportEntity>> = db.scamReportDao().getAllReports()
    val scamCount: Flow<Int> = db.scamReportDao().getTotalCount()
    val auditLogs: Flow<List<AuditLogEntity>> = db.auditLogDao().getRecentLogs()

    fun getInvestigation(id: String): Flow<InvestigationEntity?> =
        db.investigationDao().getInvestigationById(id)

    fun getFindings(investigationId: String): Flow<List<FindingEntity>> =
        db.findingDao().getFindingsForInvestigation(investigationId)

    fun searchScamReports(query: String): Flow<List<ScamReportEntity>> =
        db.scamReportDao().searchReports(query)

    suspend fun logAction(action: String, target: String, status: String, details: String) {
        db.auditLogDao().insertLog(
            AuditLogEntity(
                action = action,
                target = target,
                status = status,
                details = details,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Optional explicit demo loader for testing/evaluating reporting and comparison.
     * Never called automatically during normal production initialization.
     */
    suspend fun loadSampleDemoData() = withContext(Dispatchers.IO) {
        val id1 = "DEMO-PH-800"
        val id2 = "DEMO-UN-LINUS"

        val inv1 = InvestigationEntity(
            id = id1,
            target = "+18005550199",
            type = "PHONE",
            status = "COMPLETED",
            scanner = "PhoneInfoga & ITU-T",
            summary = "North American Toll-Free corporate routing. ITU validation confirmed.",
            riskScore = 15,
            riskLevel = "LOW",
            findingsCount = 3,
            timestamp = System.currentTimeMillis() - 3600000L,
            durationMs = 450
        )

        val inv2 = InvestigationEntity(
            id = id2,
            target = "torvalds",
            type = "USERNAME",
            status = "COMPLETED",
            scanner = "NameCheck Engine",
            summary = "Probed 20 public platforms. Verified handles located on GitHub and HackerNews.",
            riskScore = 5,
            riskLevel = "LOW",
            findingsCount = 2,
            timestamp = System.currentTimeMillis() - 1800000L,
            durationMs = 1200
        )

        db.investigationDao().insertInvestigation(inv1)
        db.investigationDao().insertInvestigation(inv2)

        db.findingDao().insertFindings(
            listOf(
                FindingEntity(
                    investigationId = id1,
                    category = "METADATA",
                    source = "ITU-T E.164 Specification",
                    title = "E.164 Geo-Allocation: United States / Canada",
                    description = "Target parsed as +18005550199. Allocated Country: United States / Canada (US), Country Code: +1.",
                    confidence = "LOCAL_VALIDATION",
                    severity = "INFO"
                ),
                FindingEntity(
                    investigationId = id1,
                    category = "CARRIER",
                    source = "NANPA Allocation Pool",
                    title = "Line Classification: Toll-Free Organization Line",
                    description = "Managed in NANPA RespOrg registry. Not assigned to personal cellular SIM.",
                    confidence = "LOCAL_VALIDATION",
                    severity = "INFO"
                )
            )
        )

        logAction("DEMO_DATA_LOAD", "SYSTEM", "SUCCESS", "User manually loaded demo telemetry for evaluation.")
    }

    suspend fun executePhoneScan(
        targetNumber: String,
        daemonUrl: String = "http://127.0.0.1:5000",
        binaryPath: String = "/data/data/com.termux/files/usr/bin/phoneinfoga",
        onProgress: suspend (step: String, percent: Float) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val investigationId = "INV-PH-" + UUID.randomUUID().toString().take(8).uppercase()
        val startTime = System.currentTimeMillis()

        val initial = InvestigationEntity(
            id = investigationId,
            target = targetNumber,
            type = "PHONE",
            status = "RUNNING",
            scanner = "PhoneInfoga & ITU-T",
            summary = "Validating ITU standard and querying PhoneInfoga...",
            timestamp = startTime
        )
        db.investigationDao().insertInvestigation(initial)
        logAction("SCAN_START", targetNumber, "SUCCESS", "Started Phone OSINT Investigation ID: $investigationId")

        try {
            val (parsed, findings) = PhoneOsintEngine.executeInvestigation(
                investigationId = investigationId,
                inputNumber = targetNumber,
                daemonUrl = daemonUrl,
                binaryPath = binaryPath,
                onProgress = onProgress
            )

            // Calculate realistic risk based on verifiable telemetry
            val isScamReported = db.scamReportDao().getReportsForNumber(parsed.e164).firstOrNull()?.isNotEmpty() == true
            val hasScannerData = findings.any { it.confidence == "SCANNER_OUTPUT" }

            val riskScore = when {
                isScamReported -> 85
                !parsed.isValidFormat -> 40
                parsed.isTollFree -> 15
                else -> 10
            }

            val riskLevel = when {
                riskScore >= 75 -> "HIGH"
                riskScore >= 40 -> "MEDIUM"
                else -> "LOW"
            }

            db.findingDao().insertFindings(findings)

            val summaryText = if (hasScannerData) {
                "${parsed.countryName} (${parsed.countryCode}) - ${parsed.lineType}. PhoneInfoga scanner active with ${findings.size} findings."
            } else {
                "${parsed.countryName} (${parsed.countryCode}) - ${parsed.lineType}. Local ITU validation complete (PhoneInfoga offline)."
            }

            val completed = initial.copy(
                status = "COMPLETED",
                summary = summaryText,
                riskScore = riskScore,
                riskLevel = riskLevel,
                findingsCount = findings.size,
                durationMs = System.currentTimeMillis() - startTime
            )
            db.investigationDao().updateInvestigation(completed)
            logAction("SCAN_FINISH", targetNumber, "SUCCESS", "Completed Phone OSINT ($riskLevel risk, ${findings.size} findings)")
            investigationId
        } catch (e: Exception) {
            val failed = initial.copy(
                status = "FAILED",
                summary = "Scan error: ${e.localizedMessage ?: "Unknown exception"}",
                durationMs = System.currentTimeMillis() - startTime
            )
            db.investigationDao().updateInvestigation(failed)
            logAction("SCAN_FINISH", targetNumber, "FAILURE", "Phone scan failed: ${e.message}")
            investigationId
        }
    }

    suspend fun executeUsernameScan(
        username: String,
        onProgress: suspend (current: Int, total: Int, platform: String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val investigationId = "INV-UN-" + UUID.randomUUID().toString().take(8).uppercase()
        val startTime = System.currentTimeMillis()

        val initial = InvestigationEntity(
            id = investigationId,
            target = username,
            type = "USERNAME",
            status = "RUNNING",
            scanner = "NameCheck Engine",
            summary = "Probing public platform footprints...",
            timestamp = startTime
        )
        db.investigationDao().insertInvestigation(initial)
        logAction("SCAN_START", username, "SUCCESS", "Started Username Investigation ID: $investigationId")

        try {
            val (results, findings) = UsernameOsintEngine.executeInvestigation(investigationId, username, onProgress)
            val foundCount = results.count { it.exists }

            db.findingDao().insertFindings(findings)

            val completed = initial.copy(
                status = "COMPLETED",
                summary = "Discovered $foundCount public profiles across ${results.size} probed services.",
                riskScore = if (foundCount > 6) 20 else 5,
                riskLevel = "LOW",
                findingsCount = findings.size,
                durationMs = System.currentTimeMillis() - startTime
            )
            db.investigationDao().updateInvestigation(completed)
            logAction("SCAN_FINISH", username, "SUCCESS", "Username scan completed ($foundCount profiles discovered)")
            investigationId
        } catch (e: Exception) {
            val failed = initial.copy(
                status = "FAILED",
                summary = "Scan aborted: ${e.localizedMessage ?: "Network error"}",
                durationMs = System.currentTimeMillis() - startTime
            )
            db.investigationDao().updateInvestigation(failed)
            logAction("SCAN_FINISH", username, "FAILURE", "Username scan failed: ${e.message}")
            investigationId
        }
    }

    suspend fun executeEmailScan(
        email: String,
        onProgress: suspend (step: String, percent: Float) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val investigationId = "INV-EM-" + UUID.randomUUID().toString().take(8).uppercase()
        val startTime = System.currentTimeMillis()

        val initial = InvestigationEntity(
            id = investigationId,
            target = email,
            type = "EMAIL",
            status = "RUNNING",
            scanner = "EmailDNS OSINT",
            summary = "Analyzing RFC compliance and domain footprint...",
            timestamp = startTime
        )
        db.investigationDao().insertInvestigation(initial)
        logAction("SCAN_START", email, "SUCCESS", "Started Email Investigation ID: $investigationId")

        try {
            val (analysis, findings) = EmailOsintEngine.executeInvestigation(investigationId, email, onProgress)

            val riskScore = when {
                analysis.isDisposable -> 80
                !analysis.isValidFormat -> 60
                else -> 10
            }
            val riskLevel = when {
                riskScore >= 75 -> "HIGH"
                riskScore >= 45 -> "MEDIUM"
                else -> "LOW"
            }

            db.findingDao().insertFindings(findings)

            val completed = initial.copy(
                status = "COMPLETED",
                summary = "${analysis.domain} (${analysis.probableDomainCategory}). ${if (analysis.isDisposable) "Flagged as disposable throwaway service!" else "Standard domain syntax."}",
                riskScore = riskScore,
                riskLevel = riskLevel,
                findingsCount = findings.size,
                durationMs = System.currentTimeMillis() - startTime
            )
            db.investigationDao().updateInvestigation(completed)
            logAction("SCAN_FINISH", email, "SUCCESS", "Email scan completed ($riskLevel risk)")
            investigationId
        } catch (e: Exception) {
            val failed = initial.copy(
                status = "FAILED",
                summary = "Scan failed: ${e.localizedMessage ?: "Analysis error"}",
                durationMs = System.currentTimeMillis() - startTime
            )
            db.investigationDao().updateInvestigation(failed)
            logAction("SCAN_FINISH", email, "FAILURE", "Email scan failed: ${e.message}")
            investigationId
        }
    }

    suspend fun executeSpiderFootScan(
        target: String,
        targetType: String,
        daemonUrl: String = "http://127.0.0.1:5001",
        onProgress: suspend (step: String, percent: Float, module: String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val investigationId = "INV-SF-" + UUID.randomUUID().toString().take(8).uppercase()
        val startTime = System.currentTimeMillis()

        val initial = InvestigationEntity(
            id = investigationId,
            target = target,
            type = "SPIDERFOOT",
            status = "RUNNING",
            scanner = "SpiderFoot Core ($daemonUrl)",
            summary = "Connecting to SpiderFoot daemon...",
            timestamp = startTime
        )
        db.investigationDao().insertInvestigation(initial)
        logAction("SCAN_START", target, "SUCCESS", "Started SpiderFoot ($targetType) ID: $investigationId")

        try {
            val (modules, findings) = SpiderFootEngine.executeScan(
                investigationId = investigationId,
                target = target,
                targetType = targetType,
                daemonUrl = daemonUrl,
                onProgress = onProgress
            )

            val isOffline = findings.any { it.title.contains("Offline", true) }
            val status = if (isOffline) "UNAVAILABLE" else "COMPLETED"
            val summary = if (isOffline) {
                "SpiderFoot Daemon at $daemonUrl is OFFLINE. Start daemon in Termux to execute modules."
            } else {
                "SpiderFoot executed on $target. Discovered ${findings.size} intelligence events."
            }

            db.findingDao().insertFindings(findings)

            val completed = initial.copy(
                status = status,
                summary = summary,
                riskScore = if (isOffline) 0 else 15,
                riskLevel = "INFO",
                findingsCount = findings.size,
                durationMs = System.currentTimeMillis() - startTime
            )
            db.investigationDao().updateInvestigation(completed)
            logAction("SCAN_FINISH", target, status, "SpiderFoot finished with status $status (${findings.size} events)")
            investigationId
        } catch (e: Exception) {
            val failed = initial.copy(
                status = "FAILED",
                summary = "Scan error: ${e.localizedMessage ?: "Module execution fault"}",
                durationMs = System.currentTimeMillis() - startTime
            )
            db.investigationDao().updateInvestigation(failed)
            logAction("SCAN_FINISH", target, "FAILURE", "SpiderFoot scan failed: ${e.message}")
            investigationId
        }
    }

    suspend fun submitScamReport(
        phoneNumber: String,
        category: String,
        evidence: String,
        isVerified: Boolean
    ) = withContext(Dispatchers.IO) {
        val report = ScamReportEntity(
            phoneNumber = phoneNumber.trim(),
            category = category,
            evidenceNotes = evidence.trim(),
            isVerified = isVerified,
            reportedBy = "Mobile SOC Operator"
        )
        db.scamReportDao().insertReport(report)
        logAction("SCAM_REPORT", phoneNumber, "SUCCESS", "Community scam report submitted under category '$category'")
    }

    suspend fun deleteInvestigation(id: String) = withContext(Dispatchers.IO) {
        db.findingDao().deleteByInvestigationId(id)
        db.investigationDao().deleteById(id)
        logAction("RECORD_DELETE", id, "SUCCESS", "Investigation deleted from local storage.")
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        db.investigationDao().deleteAll()
        db.auditLogDao().clearLogs()
        logAction("DATABASE_RESET", "ALL", "SUCCESS", "All local investigations and audit logs wiped.")
    }

    fun generateJsonReport(investigation: InvestigationEntity, findings: List<FindingEntity>): String =
        Companion.generateJsonReport(investigation, findings)

    fun generateCsvReport(investigation: InvestigationEntity, findings: List<FindingEntity>): String =
        Companion.generateCsvReport(investigation, findings)

    fun generateTextReport(investigation: InvestigationEntity, findings: List<FindingEntity>): String =
        Companion.generateTextReport(investigation, findings)

    companion object {
        fun generateJsonReport(investigation: InvestigationEntity, findings: List<FindingEntity>): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val isoDate = sdf.format(Date(investigation.timestamp))

            val findingsJson = findings.joinToString(",\n    ") { f ->
                """{
      "id": ${f.id},
      "category": "${f.category}",
      "source": "${f.source.replace("\"", "\\\"")}",
      "title": "${f.title.replace("\"", "\\\"")}",
      "description": "${f.description.replace("\"", "\\\"").replace("\n", "\\n")}",
      "confidence": "${f.confidence}",
      "severity": "${f.severity}",
      "url": ${if (f.url != null) "\"${f.url}\"" else "null"}
    }"""
            }

            return """{
  "report_metadata": {
    "platform": "NumberScope OSINT Platform",
    "version": "1.0.0-PROD",
    "investigation_id": "${investigation.id}",
    "timestamp": "$isoDate",
    "duration_ms": ${investigation.durationMs},
    "disclaimer": "This intelligence report was compiled strictly from public-source OSINT methods, published ITU numbering plans, and authorized local scanner integrations. NumberScope does not access private subscriber records or confidential KYC files."
  },
  "target": {
    "identifier": "${investigation.target}",
    "type": "${investigation.type}",
    "scanner": "${investigation.scanner}",
    "status": "${investigation.status}",
    "risk_score": ${investigation.riskScore},
    "risk_level": "${investigation.riskLevel}",
    "summary": "${investigation.summary.replace("\"", "\\\"")}"
  },
  "findings_count": ${findings.size},
  "findings": [
    $findingsJson
  ]
}"""
        }

        fun generateCsvReport(investigation: InvestigationEntity, findings: List<FindingEntity>): String {
            val sb = StringBuilder()
            sb.append("InvestigationID,Target,Type,FindingCategory,Source,Title,Confidence,Severity,URL\n")
            findings.forEach { f ->
                val cleanTitle = f.title.replace("\"", "\"\"")
                val cleanSource = f.source.replace("\"", "\"\"")
                val cleanUrl = f.url?.replace("\"", "\"\"") ?: ""
                sb.append("\"${investigation.id}\",\"${investigation.target}\",\"${investigation.type}\",\"${f.category}\",\"$cleanSource\",\"$cleanTitle\",\"${f.confidence}\",\"${f.severity}\",\"$cleanUrl\"\n")
            }
            return sb.toString()
        }

        fun generateTextReport(investigation: InvestigationEntity, findings: List<FindingEntity>): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
            val formattedDate = sdf.format(Date(investigation.timestamp))

            val sb = StringBuilder()
            sb.append("=================================================================\n")
            sb.append(" NUMBERSCOPE OSINT INVESTIGATION REPORT\n")
            sb.append(" Tagline: Investigate Public Information. Understand Digital Footprints.\n")
            sb.append("=================================================================\n\n")
            sb.append("INVESTIGATION METADATA:\n")
            sb.append("  ID:           ${investigation.id}\n")
            sb.append("  Target:       ${investigation.target}\n")
            sb.append("  Type:         ${investigation.type}\n")
            sb.append("  Scanner:      ${investigation.scanner}\n")
            sb.append("  Status:       ${investigation.status}\n")
            sb.append("  Date:         $formattedDate\n")
            sb.append("  Duration:     ${investigation.durationMs} ms\n")
            sb.append("  Risk Score:   ${investigation.riskScore}/100 (${investigation.riskLevel})\n")
            sb.append("  Summary:      ${investigation.summary}\n\n")
            sb.append("-----------------------------------------------------------------\n")
            sb.append("DISCOVERED FINDINGS (${findings.size} items):\n")
            sb.append("-----------------------------------------------------------------\n")

            findings.forEachIndexed { i, f ->
                sb.append("\n[#${i + 1}] ${f.title}\n")
                sb.append("  Category:   ${f.category}\n")
                sb.append("  Source:     ${f.source}\n")
                sb.append("  Confidence: ${f.confidence} | Severity: ${f.severity}\n")
                sb.append("  Details:    ${f.description}\n")
                if (!f.url.isNullOrEmpty()) {
                    sb.append("  Reference:  ${f.url}\n")
                }
            }

            sb.append("\n=================================================================\n")
            sb.append("ETHICAL DISCLAIMER & LIMITATIONS:\n")
            sb.append("This report was compiled strictly from publicly indexed sources, ITU\n")
            sb.append("numbering standards, DNS entries, and user-contributed reports. NumberScope\n")
            sb.append("does not access private government registries, Aadhaar, PAN, telecom\n")
            sb.append("subscriber databases, or private accounts. Unverified findings are marked.\n")
            sb.append("=================================================================\n")
            return sb.toString()
        }
    }
}
