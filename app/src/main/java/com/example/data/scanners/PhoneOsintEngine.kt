package com.example.data.scanners

import com.example.data.database.FindingEntity

data class PhoneParseResult(
    val rawInput: String,
    val e164: String,
    val countryCode: String,
    val countryName: String,
    val isoCode: String,
    val region: String,
    val nationalFormat: String,
    val lineType: String,
    val historicalCarrier: String,
    val isValidFormat: Boolean,
    val isTollFree: Boolean,
    val mnpNotice: String
)

object PhoneOsintEngine {

    private val countryPrefixes = listOf(
        CountryPrefix("1", "US", "United States / Canada", "North America", 10, 10),
        CountryPrefix("44", "GB", "United Kingdom", "Europe", 10, 10),
        CountryPrefix("91", "IN", "India", "South Asia", 10, 10),
        CountryPrefix("49", "DE", "Germany", "Europe", 10, 11),
        CountryPrefix("33", "FR", "France", "Europe", 9, 9),
        CountryPrefix("61", "AU", "Australia", "Oceania", 9, 9),
        CountryPrefix("81", "JP", "Japan", "East Asia", 10, 10),
        CountryPrefix("86", "CN", "China", "East Asia", 11, 11),
        CountryPrefix("55", "BR", "Brazil", "South America", 10, 11),
        CountryPrefix("52", "MX", "Mexico", "North America", 10, 10),
        CountryPrefix("34", "ES", "Spain", "Europe", 9, 9),
        CountryPrefix("39", "IT", "Italy", "Europe", 9, 10),
        CountryPrefix("31", "NL", "Netherlands", "Europe", 9, 9),
        CountryPrefix("46", "SE", "Sweden", "Europe", 9, 9),
        CountryPrefix("41", "CH", "Switzerland", "Europe", 9, 9),
        CountryPrefix("971", "AE", "United Arab Emirates", "Middle East", 9, 9),
        CountryPrefix("966", "SA", "Saudi Arabia", "Middle East", 9, 9),
        CountryPrefix("65", "SG", "Singapore", "Southeast Asia", 8, 8),
        CountryPrefix("60", "MY", "Malaysia", "Southeast Asia", 9, 10),
        CountryPrefix("62", "ID", "Indonesia", "Southeast Asia", 9, 12),
        CountryPrefix("63", "PH", "Philippines", "Southeast Asia", 10, 10),
        CountryPrefix("27", "ZA", "South Africa", "Africa", 9, 9),
        CountryPrefix("234", "NG", "Nigeria", "Africa", 10, 10),
        CountryPrefix("254", "KE", "Kenya", "Africa", 9, 9),
        CountryPrefix("20", "EG", "Egypt", "North Africa", 10, 10),
        CountryPrefix("92", "PK", "Pakistan", "South Asia", 10, 10),
        CountryPrefix("880", "BD", "Bangladesh", "South Asia", 10, 10),
        CountryPrefix("7", "RU", "Russia / Kazakhstan", "Eurasia", 10, 10)
    )

    data class CountryPrefix(
        val prefix: String,
        val iso: String,
        val name: String,
        val region: String,
        val minLen: Int,
        val maxLen: Int
    )

    /**
     * Sanitizes input to strict digits with leading plus.
     */
    fun sanitizeE164(input: String): String {
        val clean = input.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        val digits = clean.removePrefix("+").filter { it.isDigit() }
        return if (digits.isNotEmpty()) "+$digits" else ""
    }

    /**
     * Parses number based strictly on ITU-T E.164 recommendations and published numbering plans.
     */
    fun parse(input: String): PhoneParseResult {
        val sanitized = sanitizeE164(input)
        val digits = sanitized.removePrefix("+")

        if (digits.length < 5) {
            return PhoneParseResult(
                rawInput = input,
                e164 = sanitized,
                countryCode = "Unknown",
                countryName = "Invalid Format",
                isoCode = "??",
                region = "Global",
                nationalFormat = digits,
                lineType = "Invalid / Incomplete",
                historicalCarrier = "Unknown",
                isValidFormat = false,
                isTollFree = false,
                mnpNotice = "Number does not contain sufficient digits for ITU E.164 compliance."
            )
        }

        // Match longest prefix
        val matched = countryPrefixes
            .filter { digits.startsWith(it.prefix) }
            .maxByOrNull { it.prefix.length }
            ?: CountryPrefix("?", "UN", "International / Other", "Global", 7, 15)

        val nationalDigits = if (matched.prefix != "?") digits.substring(matched.prefix.length) else digits
        val isValidLen = nationalDigits.length in matched.minLen..matched.maxLen

        var lineType = "General Telephony"
        var isTollFree = false
        var historicalCarrier = "Unallocated / National Pool"
        var mnpNotice = "Carrier assignment is subject to Mobile Number Portability (MNP)."

        when (matched.prefix) {
            "1" -> { // North America NANPA
                if (nationalDigits.startsWith("800") || nationalDigits.startsWith("888") ||
                    nationalDigits.startsWith("877") || nationalDigits.startsWith("866") ||
                    nationalDigits.startsWith("855") || nationalDigits.startsWith("844") ||
                    nationalDigits.startsWith("833")
                ) {
                    lineType = "Toll-Free Organization Line"
                    isTollFree = true
                    historicalCarrier = "NANPA Toll-Free SMS/800 Database"
                    mnpNotice = "Toll-free numbers are managed by RespOrgs, not individual mobile SIMs."
                } else if (nationalDigits.startsWith("900")) {
                    lineType = "Premium Rate Service"
                } else {
                    lineType = "Geographic / Mobile"
                    historicalCarrier = "NANPA Block Allocation"
                }
            }
            "44" -> { // UK Ofcom
                if (nationalDigits.startsWith("7")) {
                    lineType = "Mobile Cellular"
                    historicalCarrier = "UK Mobile Allocation Pool"
                } else if (nationalDigits.startsWith("800") || nationalDigits.startsWith("808")) {
                    lineType = "Toll-Free"
                    isTollFree = true
                    historicalCarrier = "Freephone 0800 Range"
                } else if (nationalDigits.startsWith("1") || nationalDigits.startsWith("2")) {
                    lineType = "Geographic Fixed Wireline"
                    historicalCarrier = "Geographic Area Code Allocation"
                }
            }
            "91" -> { // India DoT / TRAI
                if (nationalDigits.firstOrNull() in listOf('6', '7', '8', '9')) {
                    lineType = "Mobile Cellular (DoT Series)"
                    historicalCarrier = "Indian Mobile Operator Series"
                    mnpNotice = "CRITICAL: Nationwide Mobile Number Portability (MNP) is active across all Indian telecom circles. Real-time carrier operator cannot be deduced from prefixes and requires live HLR / PhoneInfoga verification."
                } else {
                    lineType = "Fixed Landline / Wireline (STD)"
                    historicalCarrier = "BSNL / MTNL / Private Wireline"
                }
            }
            "49" -> { // Germany BNetzA
                if (nationalDigits.startsWith("15") || nationalDigits.startsWith("16") || nationalDigits.startsWith("17")) {
                    lineType = "Mobile Cellular"
                    historicalCarrier = "German Mobile Pool (BNetzA)"
                } else {
                    lineType = "Geographic Fixed Line"
                }
            }
            else -> {
                historicalCarrier = "National Telecom Allocation (${matched.name})"
            }
        }

        val formattedNational = if (nationalDigits.length == 10) {
            "(${nationalDigits.substring(0, 3)}) ${nationalDigits.substring(3, 6)}-${nationalDigits.substring(6)}"
        } else {
            nationalDigits.chunked(4).joinToString(" ")
        }

        return PhoneParseResult(
            rawInput = input,
            e164 = "+$digits",
            countryCode = "+${matched.prefix}",
            countryName = matched.name,
            isoCode = matched.iso,
            region = matched.region,
            nationalFormat = formattedNational,
            lineType = lineType,
            historicalCarrier = historicalCarrier,
            isValidFormat = isValidLen,
            isTollFree = isTollFree,
            mnpNotice = mnpNotice
        )
    }

    /**
     * Executes authentic investigation:
     * 1. Evaluates genuine local ITU standards.
     * 2. Attempts PhoneInfoga CLI/REST integration.
     * 3. Attaches generated reference search queries, explicitly marked as UNVERIFIED queries.
     */
    suspend fun executeInvestigation(
        investigationId: String,
        inputNumber: String,
        daemonUrl: String = "http://127.0.0.1:5000",
        binaryPath: String = "/data/data/com.termux/files/usr/bin/phoneinfoga",
        onProgress: suspend (step: String, percent: Float) -> Unit
    ): Pair<PhoneParseResult, List<FindingEntity>> {
        val findings = mutableListOf<FindingEntity>()

        onProgress("Validating ITU-T E.164 Numbering Plan...", 0.20f)
        val parsed = parse(inputNumber)

        findings.add(
            FindingEntity(
                investigationId = investigationId,
                category = "METADATA",
                source = "ITU-T E.164 Standard Specification",
                title = "E.164 Geo-Allocation: ${parsed.countryName}",
                description = "Target parsed as ${parsed.e164}. Allocated Country: ${parsed.countryName} (${parsed.isoCode}), Country Code: ${parsed.countryCode}. Format validation: ${if (parsed.isValidFormat) "Valid Length" else "Non-standard Length"}.",
                confidence = "LOCAL_VALIDATION",
                severity = if (parsed.isValidFormat) "INFO" else "MEDIUM"
            )
        )

        findings.add(
            FindingEntity(
                investigationId = investigationId,
                category = "CARRIER",
                source = "National Numbering Plan Specification",
                title = "Line Classification: ${parsed.lineType}",
                description = "${parsed.mnpNotice} Initial historical series: ${parsed.historicalCarrier}.",
                confidence = "LOCAL_VALIDATION",
                severity = "INFO"
            )
        )

        // Step 2: Attempt Real PhoneInfoga Scan
        onProgress("Querying PhoneInfoga Scanner ($daemonUrl)...", 0.50f)
        val phoneInfogaResult = PhoneInfogaClient.scan(
            investigationId = investigationId,
            sanitizedE164 = parsed.e164,
            daemonUrl = daemonUrl,
            binaryPath = binaryPath
        )

        when (phoneInfogaResult) {
            is PhoneInfogaScanResult.Success -> {
                findings.addAll(phoneInfogaResult.findings)
                findings.add(
                    FindingEntity(
                        investigationId = investigationId,
                        category = "SCANNER_DATA",
                        source = "PhoneInfoga Scanner",
                        title = "PhoneInfoga Scan Succeeded (${phoneInfogaResult.version})",
                        description = "Scanner completed execution. Carrier: ${phoneInfogaResult.carrier ?: "Not returned"}, Line Type: ${phoneInfogaResult.lineType ?: "Not returned"}.",
                        confidence = "SCANNER_OUTPUT",
                        severity = "INFO"
                    )
                )
            }
            is PhoneInfogaScanResult.Unavailable -> {
                findings.add(
                    FindingEntity(
                        investigationId = investigationId,
                        category = "SCANNER_DATA",
                        source = "PhoneInfoga Status Diagnostics",
                        title = "PhoneInfoga Scanner Unavailable",
                        description = phoneInfogaResult.diagnostics,
                        confidence = "LOCAL_VALIDATION",
                        severity = "LOW"
                    )
                )
            }
            is PhoneInfogaScanResult.Error -> {
                findings.add(
                    FindingEntity(
                        investigationId = investigationId,
                        category = "SCANNER_DATA",
                        source = "PhoneInfoga Error Handler",
                        title = "PhoneInfoga Error: ${phoneInfogaResult.message}",
                        description = phoneInfogaResult.details ?: "No additional diagnostic output returned by scanner.",
                        confidence = "SCANNER_OUTPUT",
                        severity = "MEDIUM"
                    )
                )
            }
        }

        // Step 3: Reference Search Queries (Explicitly marked as REFERENCE_QUERY, NEVER as VERIFIED findings)
        onProgress("Generating Public Search References...", 0.80f)
        val rawClean = parsed.e164.removePrefix("+")

        val dorkQuery = "https://www.google.com/search?q=\"${parsed.e164}\"+OR+\"${parsed.nationalFormat}\""
        findings.add(
            FindingEntity(
                investigationId = investigationId,
                category = "REFERENCE_QUERY",
                source = "Open Web Search Dork",
                title = "Search Reference: Exact Phone String Query",
                description = "Generated Google query to check for publicly crawled listings, forum posts, or directory pages. (Requires manual verification).",
                url = dorkQuery,
                confidence = "REFERENCE_QUERY",
                severity = "INFO"
            )
        )

        val pastebinDork = "https://www.google.com/search?q=site:pastebin.com+\"$rawClean\""
        findings.add(
            FindingEntity(
                investigationId = investigationId,
                category = "REFERENCE_QUERY",
                source = "Open Paste Search Dork",
                title = "Search Reference: Pastebin Mentions Query",
                description = "Generated search URL to check unauthenticated paste repositories for public phone references.",
                url = pastebinDork,
                confidence = "REFERENCE_QUERY",
                severity = "INFO"
            )
        )

        // Messaging URIs marked strictly as unverified direct links
        val waLink = "https://wa.me/$rawClean"
        findings.add(
            FindingEntity(
                investigationId = investigationId,
                category = "REFERENCE_QUERY",
                source = "WhatsApp Web URI Protocol",
                title = "Direct Link: WhatsApp Messaging Vector",
                description = "Public direct-communication link. Does NOT confirm whether an active account is registered.",
                url = waLink,
                confidence = "REFERENCE_QUERY",
                severity = "INFO"
            )
        )

        onProgress("Phone Investigation Complete.", 1.0f)
        return Pair(parsed, findings)
    }
}
