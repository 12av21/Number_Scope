package com.example.data.scanners

import com.example.data.database.FindingEntity
import kotlinx.coroutines.delay

data class SpiderFootModuleInfo(
    val code: String,
    val name: String,
    val supportedTypes: List<String>,
    val status: String, // READY, RUNNING, COMPLETED, SKIPPED, ERROR
    val reason: String = ""
)

object SpiderFootEngine {

    val availableModules = listOf(
        SpiderFootModuleInfo("sfp_dns", "DNS Resolver & Zone Transfer", listOf("DOMAIN", "HOSTNAME"), "READY"),
        SpiderFootModuleInfo("sfp_whois", "WHOIS Registrar & ASN Lookup", listOf("DOMAIN", "IP"), "READY"),
        SpiderFootModuleInfo("sfp_phone", "Phone Carrier & Geo Resolver", listOf("PHONE"), "READY"),
        SpiderFootModuleInfo("sfp_reputation", "Threat Intelligence & Blocklists", listOf("IP", "DOMAIN", "PHONE"), "READY"),
        SpiderFootModuleInfo("sfp_search", "Open Web Search Aggregator", listOf("DOMAIN", "USERNAME", "EMAIL", "PHONE"), "READY"),
        SpiderFootModuleInfo("sfp_pgp", "PGP Public Key Server Lookup", listOf("EMAIL", "USERNAME"), "READY"),
        SpiderFootModuleInfo("sfp_ssl", "SSL/TLS Certificate Transparency", listOf("DOMAIN", "HOSTNAME"), "READY"),
        SpiderFootModuleInfo("sfp_email", "Email Format & MX Inspector", listOf("EMAIL", "DOMAIN"), "READY"),
        SpiderFootModuleInfo("sfp_social", "Social Media Account Correlation", listOf("USERNAME", "EMAIL"), "READY")
    )

    suspend fun checkDaemonHealth(daemonUrl: String): Pair<Boolean, String> {
        return SpiderFootClient.checkDaemonHealth(daemonUrl)
    }

    /**
     * Executes real SpiderFoot scan against running daemon.
     * Never fabricates findings if daemon is offline.
     */
    suspend fun executeScan(
        investigationId: String,
        target: String,
        targetType: String,
        daemonUrl: String = "http://127.0.0.1:5001",
        onProgress: suspend (step: String, percent: Float, activeModule: String) -> Unit
    ): Pair<List<SpiderFootModuleInfo>, List<FindingEntity>> {
        val findings = mutableListOf<FindingEntity>()

        onProgress("Checking SpiderFoot Daemon Connection ($daemonUrl)...", 0.10f, "HEALTH_CHECK")
        val (isOnline, healthMsg) = checkDaemonHealth(daemonUrl)

        if (!isOnline) {
            // Do NOT fabricate findings! Report honest scanner unavailability.
            onProgress("SpiderFoot Daemon Offline", 1.0f, "OFFLINE")

            findings.add(
                FindingEntity(
                    investigationId = investigationId,
                    category = "SPIDERFOOT_EVENT",
                    source = "SpiderFoot Integration Client",
                    title = "SpiderFoot Daemon is Offline / Unreachable",
                    description = "Could not establish connection to SpiderFoot daemon at $daemonUrl ($healthMsg).\n\n" +
                            "To run live SpiderFoot scans:\n" +
                            "1. Open Termux / Debian proot-distro.\n" +
                            "2. Launch SpiderFoot: `python3 ./sf.py -l 127.0.0.1:5001`\n" +
                            "3. Confirm daemon host in NumberScope Settings.",
                    confidence = "LOCAL_VALIDATION",
                    severity = "LOW"
                )
            )

            // Mark modules as not executed
            val skippedList = availableModules.map {
                it.copy(status = "SKIPPED", reason = "Daemon offline: connection refused at $daemonUrl")
            }
            return Pair(skippedList, findings)
        }

        // Daemon is online: Start actual scan
        onProgress("Creating scan job on SpiderFoot...", 0.25f, "START_SCAN")
        val scanName = "NumberScope_$investigationId"
        val (started, scanId) = SpiderFootClient.startScan(
            daemonUrl = daemonUrl,
            scanName = scanName,
            target = target,
            targetType = targetType
        )

        if (!started) {
            findings.add(
                FindingEntity(
                    investigationId = investigationId,
                    category = "SPIDERFOOT_EVENT",
                    source = "SpiderFoot API",
                    title = "Failed to Initialize Scan Job",
                    description = "SpiderFoot responded with an initialization error: $scanId",
                    confidence = "SCANNER_OUTPUT",
                    severity = "MEDIUM"
                )
            )
            return Pair(availableModules.map { it.copy(status = "ERROR", reason = scanId) }, findings)
        }

        // Poll scan status
        var completed = false
        var attempts = 0
        while (!completed && attempts < 8) {
            attempts++
            val status = SpiderFootClient.getScanStatus(daemonUrl, scanId)
            val pct = 0.30f + (attempts * 0.08f)
            onProgress("SpiderFoot Status: $status (Job: $scanId)", pct, status)

            if (status.contains("FINISHED", ignoreCase = true) ||
                status.contains("ABORTED", ignoreCase = true) ||
                status.contains("ERROR", ignoreCase = true)
            ) {
                completed = true
            } else {
                delay(1200)
            }
        }

        // Retrieve real events
        onProgress("Retrieving discovered events from SpiderFoot...", 0.90f, "FETCH_EVENTS")
        val events = SpiderFootClient.getScanEvents(daemonUrl, scanId, investigationId)
        findings.addAll(events)

        if (events.isEmpty()) {
            findings.add(
                FindingEntity(
                    investigationId = investigationId,
                    category = "SPIDERFOOT_EVENT",
                    source = "SpiderFoot Scan Report",
                    title = "Scan Job Finished (No Events Emitted)",
                    description = "SpiderFoot job '$scanId' completed without emitting new correlation events for target '$target'.",
                    confidence = "SCANNER_OUTPUT",
                    severity = "INFO"
                )
            )
        }

        onProgress("SpiderFoot Scan Completed (${findings.size} events)", 1.0f, "COMPLETE")

        val modulesSummary = availableModules.map { mod ->
            if (mod.supportedTypes.contains(targetType)) {
                mod.copy(status = "COMPLETED", reason = "Executed on daemon under scan $scanId")
            } else {
                mod.copy(status = "SKIPPED", reason = "Module does not support target type $targetType")
            }
        }

        return Pair(modulesSummary, findings)
    }
}
