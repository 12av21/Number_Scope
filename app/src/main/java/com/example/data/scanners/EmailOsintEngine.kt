package com.example.data.scanners

import com.example.data.database.FindingEntity
import kotlinx.coroutines.delay

data class EmailAnalysisResult(
    val email: String,
    val localPart: String,
    val domain: String,
    val isValidFormat: Boolean,
    val isDisposable: Boolean,
    val isFreeWebmail: Boolean,
    val probableDomainCategory: String
)

object EmailOsintEngine {

    private val disposableDomains = setOf(
        "mailinator.com", "10minutemail.com", "tempmail.com", "guerrillamail.com",
        "throwawaymail.com", "trashmail.com", "getairmail.com", "yopmail.com",
        "sharklasers.com", "dispostable.com", "fakemailgenerator.com", "temp-mail.org",
        "crazymailing.com", "mohmal.com", "burnermail.io", "nada.ltd"
    )

    private val freeWebmailDomains = setOf(
        "gmail.com", "googlemail.com", "yahoo.com", "hotmail.com", "outlook.com",
        "live.com", "icloud.com", "proton.me", "protonmail.com", "zoho.com",
        "aol.com", "mail.com", "gmx.com", "yandex.com", "tutanota.com"
    )

    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")

    fun analyze(emailInput: String): EmailAnalysisResult {
        val clean = emailInput.trim().lowercase()
        val isValid = emailRegex.matches(clean)
        val parts = clean.split("@")
        val localPart = parts.getOrNull(0) ?: ""
        val domain = parts.getOrNull(1) ?: ""

        val isDisposable = disposableDomains.contains(domain)
        val isFree = freeWebmailDomains.contains(domain)

        val category = when {
            isDisposable -> "Disposable / Temporary Throwaway Service"
            isFree -> "Free Public Webmail Provider"
            domain.endsWith(".edu") || domain.endsWith(".ac.in") || domain.endsWith(".ac.uk") -> "Academic / Educational Institution"
            domain.endsWith(".gov") || domain.endsWith(".mil") -> "Government / Military Entity"
            domain.isNotEmpty() -> "Custom Corporate / Private Domain"
            else -> "Invalid"
        }

        return EmailAnalysisResult(
            email = clean,
            localPart = localPart,
            domain = domain,
            isValidFormat = isValid,
            isDisposable = isDisposable,
            isFreeWebmail = isFree,
            probableDomainCategory = category
        )
    }

    suspend fun executeInvestigation(
        investigationId: String,
        emailInput: String,
        onProgress: suspend (step: String, percent: Float) -> Unit
    ): Pair<EmailAnalysisResult, List<FindingEntity>> {
        val findings = mutableListOf<FindingEntity>()

        onProgress("Validating RFC 5322 Syntax...", 0.20f)
        delay(300)
        val analysis = analyze(emailInput)

        findings.add(
            FindingEntity(
                investigationId = investigationId,
                category = "METADATA",
                source = "RFC 5322 Standard Email Syntax",
                title = "Email Syntax & Format Assessment",
                description = "Format check: ${if (analysis.isValidFormat) "Compliant" else "Malformed"}. Local part: '${analysis.localPart}', Domain host: '${analysis.domain}'.",
                confidence = if (analysis.isValidFormat) "VERIFIED" else "LOW",
                severity = if (analysis.isValidFormat) "INFO" else "HIGH"
            )
        )

        onProgress("Inspecting Mail Domain Reputation & Categorization...", 0.45f)
        delay(350)

        val domainSeverity = when {
            analysis.isDisposable -> "HIGH"
            !analysis.isValidFormat -> "HIGH"
            else -> "INFO"
        }

        findings.add(
            FindingEntity(
                investigationId = investigationId,
                category = "REPUTATION",
                source = "Domain Categorization Engine",
                title = "Domain Classification: ${analysis.probableDomainCategory}",
                description = if (analysis.isDisposable) {
                    "ALERT: Domain '${analysis.domain}' is classified as a disposable / temporary inbox service often associated with burner accounts or fraud."
                } else {
                    "Domain '${analysis.domain}' is standard infrastructure (${analysis.probableDomainCategory})."
                },
                confidence = "VERIFIED",
                severity = domainSeverity
            )
        )

        onProgress("Generating Public Breach and Pastebin Footprint Queries...", 0.70f)
        delay(350)

        val pastebinDork = "https://www.google.com/search?q=site:pastebin.com+\"${analysis.email}\""
        findings.add(
            FindingEntity(
                investigationId = investigationId,
                category = "BREACH_INDEX",
                source = "Public Paste Scraping Dork",
                title = "Pastebin & Dump Mentions Dork",
                description = "Check for public plaintext credentials, configuration files, and forum dumps exposing '${analysis.email}'.",
                url = pastebinDork,
                confidence = "HIGH",
                severity = "LOW"
            )
        )

        val githubDork = "https://www.google.com/search?q=site:github.com+\"${analysis.email}\""
        findings.add(
            FindingEntity(
                investigationId = investigationId,
                category = "DORKS",
                source = "GitHub Commit & Gist Index",
                title = "Git Commit Author & Code Reference Dork",
                description = "Search public open-source Git commits, repository issues, and pull requests authored with '${analysis.email}'.",
                url = githubDork,
                confidence = "HIGH",
                severity = "INFO"
            )
        )

        val domainDork = "https://www.google.com/search?q=\"@${analysis.domain}\""
        findings.add(
            FindingEntity(
                investigationId = investigationId,
                category = "DORKS",
                source = "Domain Footprint Query",
                title = "Public Organization Footprint for @${analysis.domain}",
                description = "Search all publicly listed email addresses and documents on '${analysis.domain}'.",
                url = domainDork,
                confidence = "HIGH",
                severity = "INFO"
            )
        )

        onProgress("Investigation Complete. Summarizing Results.", 1.0f)
        delay(200)

        return Pair(analysis, findings)
    }
}
