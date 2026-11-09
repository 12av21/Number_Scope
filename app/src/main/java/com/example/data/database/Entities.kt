package com.example.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an OSINT investigation session.
 * Status values:
 * - COMPLETED: Scanner finished with results
 * - RUNNING: Scanner is actively executing
 * - FAILED: Scanner encountered an error or target was invalid
 * - UNAVAILABLE: External scanner (PhoneInfoga or SpiderFoot) was unreachable/offline
 */
@Entity(tableName = "investigations")
data class InvestigationEntity(
    @PrimaryKey val id: String,
    val target: String,
    val type: String, // PHONE, SPIDERFOOT, USERNAME, EMAIL, SCAM
    val status: String, // COMPLETED, RUNNING, FAILED, UNAVAILABLE
    val scanner: String, // PhoneInfoga REST/CLI, SpiderFoot Core, NameCheck Engine, EmailDNS OSINT, etc.
    val summary: String,
    val riskScore: Int = 0, // 0 - 100
    val riskLevel: String = "INFO", // LOW, MEDIUM, HIGH, CRITICAL, INFO
    val findingsCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val notes: String = ""
)

/**
 * Finding item with strict confidence and origin attribution:
 * Categories:
 * - METADATA: Local ITU numbering or RFC standard parsing
 * - CARRIER: Carrier allocation from numbering plan or live scanner
 * - SCANNER_DATA: Real response from PhoneInfoga or SpiderFoot
 * - PROFILE: Confirmed public user profile
 * - REFERENCE_QUERY: Generated Google Dork or search query (NOT a verified account!)
 * - REPUTATION: Blacklist or threat feed entry
 * - BREACH_INDEX: Public breach index query
 * - SPIDERFOOT_EVENT: Real event emitted by SpiderFoot engine
 *
 * Confidence:
 * - VERIFIED: Factually verified via authoritatively signed API or live confirmed endpoint
 * - SCANNER_OUTPUT: Direct raw output from external scanner tool
 * - LOCAL_VALIDATION: Calculated locally using published ITU/RFC standards
 * - REFERENCE_QUERY: Search dork / link (requires manual investigator verification)
 * - UNVERIFIED: Uncorroborated community submission
 */
@Entity(
    tableName = "findings",
    foreignKeys = [
        ForeignKey(
            entity = InvestigationEntity::class,
            parentColumns = ["id"],
            childColumns = ["investigationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["investigationId"])]
)
data class FindingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val investigationId: String,
    val category: String,
    val source: String,
    val title: String,
    val description: String,
    val url: String? = null,
    val confidence: String = "LOCAL_VALIDATION", // VERIFIED, SCANNER_OUTPUT, LOCAL_VALIDATION, REFERENCE_QUERY, UNVERIFIED
    val severity: String = "INFO", // INFO, LOW, MEDIUM, HIGH, CRITICAL
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "scam_reports")
data class ScamReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val category: String, // Robocall, Phishing, Tech Support, Delivery SMS, Crypto, Impersonation
    val reportDate: Long = System.currentTimeMillis(),
    val evidenceNotes: String,
    val source: String = "Community Report",
    val isVerified: Boolean = false,
    val reportedBy: String = "Local User"
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String, // SCAN_START, SCAN_FINISH, REPORT_EXPORT, CONFIG_CHANGE, RECORD_DELETE, SCANNER_ERROR
    val target: String,
    val status: String, // SUCCESS, FAILURE, WARN, UNAVAILABLE
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
