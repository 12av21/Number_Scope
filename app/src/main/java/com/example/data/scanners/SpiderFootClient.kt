package com.example.data.scanners

import com.example.data.database.FindingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class SpiderFootScanResult {
    data class Success(
        val scanId: String,
        val status: String,
        val eventsCount: Int,
        val findings: List<FindingEntity>,
        val modulesRan: List<String>
    ) : SpiderFootScanResult()

    data class Error(
        val message: String,
        val details: String? = null
    ) : SpiderFootScanResult()

    data class Unavailable(
        val reason: String,
        val diagnostics: String
    ) : SpiderFootScanResult()
}

object SpiderFootClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Map friendly target type to SpiderFoot internal target type string.
     */
    fun mapTargetType(type: String): String {
        return when (type.uppercase()) {
            "IP" -> "IP_ADDRESS"
            "DOMAIN" -> "DOMAIN_NAME"
            "HOSTNAME" -> "INTERNET_NAME"
            "PHONE" -> "PHONE_NUMBER"
            "EMAIL" -> "EMAILADDR"
            "USERNAME" -> "HUMAN_NAME"
            else -> "INTERNET_NAME"
        }
    }

    /**
     * Checks if SpiderFoot daemon is listening and responsive.
     */
    suspend fun checkDaemonHealth(daemonUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = daemonUrl.trimEnd('/')
        try {
            // Test /api/version first
            val req = Request.Builder().url("$cleanUrl/api/version").build()
            val res = httpClient.newCall(req).execute()
            val body = res.body?.string() ?: ""
            val code = res.code
            res.close()

            if (res.isSuccessful) {
                Pair(true, "SpiderFoot Daemon Online (version endpoint: ${body.take(50)})")
            } else {
                // Fallback test root endpoint
                val reqRoot = Request.Builder().url(cleanUrl).build()
                val resRoot = httpClient.newCall(reqRoot).execute()
                val rootBody = resRoot.body?.string() ?: ""
                val isSf = resRoot.isSuccessful && rootBody.contains("SpiderFoot", ignoreCase = true)
                resRoot.close()

                if (isSf) {
                    Pair(true, "SpiderFoot Web Server Online at $cleanUrl")
                } else {
                    Pair(false, "Server responded with HTTP $code: ${body.take(100)}")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Offline / Unreachable at $cleanUrl: ${e.localizedMessage ?: "Connection refused"}")
        }
    }

    /**
     * Initiates a real SpiderFoot scan against the running daemon.
     */
    suspend fun startScan(
        daemonUrl: String,
        scanName: String,
        target: String,
        targetType: String,
        modules: List<String> = emptyList()
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = daemonUrl.trimEnd('/')
        try {
            val sfType = mapTargetType(targetType)
            val formBuilder = FormBody.Builder()
                .add("scanname", scanName)
                .add("scantarget", target)
                .add("targettype", sfType)

            if (modules.isNotEmpty()) {
                formBuilder.add("modulelist", modules.joinToString(","))
            }

            val req = Request.Builder()
                .url("$cleanUrl/startscan")
                .post(formBuilder.build())
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string() ?: ""
            val code = res.code
            res.close()

            if (res.isSuccessful) {
                // Parse returned scan_id
                val scanId = try {
                    JSONObject(body).optString("id", JSONObject(body).optString("scan_id", ""))
                } catch (_: Exception) {
                    // Check if body contains scan ID regex
                    val regex = Regex("id=([a-zA-Z0-9_-]+)")
                    regex.find(body)?.groupValues?.getOrNull(1) ?: ""
                }

                if (scanId.isNotBlank()) {
                    Pair(true, scanId)
                } else {
                    Pair(true, "SCAN_${System.currentTimeMillis()}")
                }
            } else {
                Pair(false, "Failed to create scan (HTTP $code: ${body.take(100)})")
            }
        } catch (e: Exception) {
            Pair(false, "Network error starting scan: ${e.localizedMessage}")
        }
    }

    /**
     * Checks status of an ongoing SpiderFoot scan:
     * Returns "STARTING", "RUNNING", "FINISHED", "ERROR", etc.
     */
    suspend fun getScanStatus(daemonUrl: String, scanId: String): String = withContext(Dispatchers.IO) {
        val cleanUrl = daemonUrl.trimEnd('/')
        try {
            val req = Request.Builder()
                .url("$cleanUrl/scanstatus?id=$scanId")
                .build()
            val res = httpClient.newCall(req).execute()
            val body = res.body?.string()?.trim() ?: ""
            res.close()

            if (res.isSuccessful) {
                try {
                    JSONObject(body).optString("status", body)
                } catch (_: Exception) {
                    body.take(50)
                }
            } else {
                "UNKNOWN_HTTP_${res.code}"
            }
        } catch (e: Exception) {
            "UNREACHABLE"
        }
    }

    /**
     * Retrieves actual events discovered by SpiderFoot.
     */
    suspend fun getScanEvents(
        daemonUrl: String,
        scanId: String,
        investigationId: String
    ): List<FindingEntity> = withContext(Dispatchers.IO) {
        val cleanUrl = daemonUrl.trimEnd('/')
        val findings = mutableListOf<FindingEntity>()

        try {
            val req = Request.Builder()
                .url("$cleanUrl/scaneventresults?id=$scanId")
                .build()
            val res = httpClient.newCall(req).execute()
            val body = res.body?.string() ?: ""
            res.close()

            if (!res.isSuccessful || body.isBlank()) {
                return@withContext emptyList()
            }

            // SpiderFoot returns JSON array of events
            try {
                val array = JSONArray(body)
                for (i in 0 until array.length()) {
                    val item = array.opt(i)
                    if (item is JSONObject) {
                        val type = item.optString("type", "SPIDERFOOT_EVENT")
                        val module = item.optString("module", "SpiderFoot")
                        val data = item.optString("data", "")
                        val conf = item.optString("confidence", "HIGH")

                        findings.add(
                            FindingEntity(
                                investigationId = investigationId,
                                category = "SPIDERFOOT_EVENT",
                                source = "SpiderFoot Module: $module",
                                title = "$type: ${data.take(60)}",
                                description = "SpiderFoot Discovered Event: $type\nData: $data\nModule: $module",
                                confidence = "SCANNER_OUTPUT",
                                severity = if (type.contains("LEAK", true) || type.contains("MALWARE", true)) "HIGH" else "INFO"
                            )
                        )
                    } else if (item is JSONArray) {
                        // Array format: [time, type, data, source, module, conf, ...]
                        val type = item.optString(1, "SPIDERFOOT_EVENT")
                        val data = item.optString(2, "")
                        val module = item.optString(4, "SpiderFoot")

                        findings.add(
                            FindingEntity(
                                investigationId = investigationId,
                                category = "SPIDERFOOT_EVENT",
                                source = "SpiderFoot Module: $module",
                                title = "$type: ${data.take(60)}",
                                description = "Discovered Event: $type\nData: $data",
                                confidence = "SCANNER_OUTPUT",
                                severity = "INFO"
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // If not JSON array, log plain text
                findings.add(
                    FindingEntity(
                        investigationId = investigationId,
                        category = "SPIDERFOOT_EVENT",
                        source = "SpiderFoot Output",
                        title = "SpiderFoot Raw Response",
                        description = body.take(300),
                        confidence = "SCANNER_OUTPUT",
                        severity = "INFO"
                    )
                )
            }
        } catch (_: Exception) {}

        findings
    }
}
