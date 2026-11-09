package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.AuditLogEntity
import com.example.data.database.FindingEntity
import com.example.data.database.InvestigationEntity
import com.example.data.database.ScamReportEntity
import com.example.data.repository.InvestigationRepository
import com.example.data.scanners.PhoneInfogaClient
import com.example.data.scanners.SpiderFootClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen(val title: String) {
    DASHBOARD("SOC Dashboard"),
    PHONE_INTEL("Phone Intelligence"),
    SPIDERFOOT("SpiderFoot Core"),
    USERNAME_INTEL("Username Recon"),
    EMAIL_INTEL("Email OSINT"),
    SCAM_INTEL("Scam Intelligence"),
    HISTORY("Investigation Archive"),
    COMPARISON("Compare Scans"),
    REPORT_CENTER("Report Dossier"),
    SETTINGS("Settings & Daemons")
}

data class ScanProgressState(
    val isScanning: Boolean = false,
    val target: String = "",
    val step: String = "",
    val progress: Float = 0f,
    val details: String = "",
    val completedInvestigationId: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NumberScopeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val repository = InvestigationRepository(db)

    // Navigation and Active Selection
    private val _currentScreen = MutableStateFlow(Screen.DASHBOARD)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _userConsentGiven = MutableStateFlow(false)
    val userConsentGiven: StateFlow<Boolean> = _userConsentGiven.asStateFlow()

    private val _activeInvestigationId = MutableStateFlow<String?>(null)
    val activeInvestigationId: StateFlow<String?> = _activeInvestigationId.asStateFlow()

    private val _comparisonId1 = MutableStateFlow<String?>(null)
    val comparisonId1: StateFlow<String?> = _comparisonId1.asStateFlow()

    private val _comparisonId2 = MutableStateFlow<String?>(null)
    val comparisonId2: StateFlow<String?> = _comparisonId2.asStateFlow()

    private val _scanProgress = MutableStateFlow(ScanProgressState())
    val scanProgress: StateFlow<ScanProgressState> = _scanProgress.asStateFlow()

    // Configurable Endpoints
    private val _phoneInfogaDaemonUrl = MutableStateFlow("http://127.0.0.1:5000")
    val phoneInfogaDaemonUrl: StateFlow<String> = _phoneInfogaDaemonUrl.asStateFlow()

    private val _phoneInfogaBinaryPath = MutableStateFlow("/data/data/com.termux/files/usr/bin/phoneinfoga")
    val phoneInfogaBinaryPath: StateFlow<String> = _phoneInfogaBinaryPath.asStateFlow()

    private val _spiderFootDaemonUrl = MutableStateFlow("http://127.0.0.1:5001")
    val spiderFootDaemonUrl: StateFlow<String> = _spiderFootDaemonUrl.asStateFlow()

    private val _spiderFootStatus = MutableStateFlow("Unchecked (Tap Test to probe 127.0.0.1:5001)")
    val spiderFootStatus: StateFlow<String> = _spiderFootStatus.asStateFlow()

    private val _phoneInfogaStatus = MutableStateFlow("Unchecked (Tap Test to probe 127.0.0.1:5000)")
    val phoneInfogaStatus: StateFlow<String> = _phoneInfogaStatus.asStateFlow()

    // Database Flows
    val allInvestigations: StateFlow<List<InvestigationEntity>> = repository.allInvestigations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentInvestigations: StateFlow<List<InvestigationEntity>> = repository.recentInvestigations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = repository.totalCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val completedCount: StateFlow<Int> = repository.completedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val runningCount: StateFlow<Int> = repository.runningCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = repository.failedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val scamReports: StateFlow<List<ScamReportEntity>> = repository.allScamReports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentInvestigation: StateFlow<InvestigationEntity?> = _activeInvestigationId
        .flatMapLatest { id ->
            if (id != null) repository.getInvestigation(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentFindings: StateFlow<List<FindingEntity>> = _activeInvestigationId
        .flatMapLatest { id ->
            if (id != null) repository.getFindings(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Production initialization starts CLEAN without fake seeded records.
        viewModelScope.launch {
            repository.logAction("APP_LAUNCH", "NumberScope", "SUCCESS", "Application initialized in local-first secure mode.")
            // Quick async background health check
            testPhoneInfogaConnection()
            testSpiderFootConnection()
        }
    }

    fun setScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun acceptConsent() {
        _userConsentGiven.value = true
    }

    fun selectInvestigation(id: String) {
        _activeInvestigationId.value = id
        _currentScreen.value = Screen.REPORT_CENTER
    }

    fun setSpiderFootDaemonUrl(url: String) {
        _spiderFootDaemonUrl.value = url.trim()
    }

    fun setPhoneInfogaDaemonUrl(url: String) {
        _phoneInfogaDaemonUrl.value = url.trim()
    }

    fun setPhoneInfogaBinaryPath(path: String) {
        _phoneInfogaBinaryPath.value = path.trim()
    }

    fun testSpiderFootConnection() {
        viewModelScope.launch {
            _spiderFootStatus.value = "Connecting to ${_spiderFootDaemonUrl.value}..."
            val (ok, msg) = SpiderFootClient.checkDaemonHealth(_spiderFootDaemonUrl.value)
            _spiderFootStatus.value = if (ok) "ONLINE: $msg" else "OFFLINE: $msg"
        }
    }

    fun testPhoneInfogaConnection() {
        viewModelScope.launch {
            _phoneInfogaStatus.value = "Probing ${_phoneInfogaDaemonUrl.value} & ${_phoneInfogaBinaryPath.value}..."
            val (daemonOk, daemonMsg) = PhoneInfogaClient.checkDaemonHealth(_phoneInfogaDaemonUrl.value)
            if (daemonOk) {
                _phoneInfogaStatus.value = "ONLINE: $daemonMsg"
            } else {
                val (binOk, binMsg) = PhoneInfogaClient.checkBinaryAvailability(_phoneInfogaBinaryPath.value)
                _phoneInfogaStatus.value = if (binOk) "CLI READY: $binMsg" else "OFFLINE: $daemonMsg. ($binMsg)"
            }
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            repository.loadSampleDemoData()
        }
    }

    fun startPhoneInvestigation(number: String) {
        if (_scanProgress.value.isScanning) return
        viewModelScope.launch {
            _scanProgress.value = ScanProgressState(isScanning = true, target = number, step = "Validating E.164 and checking PhoneInfoga...", progress = 0.10f)
            val invId = repository.executePhoneScan(
                targetNumber = number,
                daemonUrl = _phoneInfogaDaemonUrl.value,
                binaryPath = _phoneInfogaBinaryPath.value
            ) { step, pct ->
                _scanProgress.value = _scanProgress.value.copy(step = step, progress = pct)
            }
            _scanProgress.value = ScanProgressState(isScanning = false, completedInvestigationId = invId)
            selectInvestigation(invId)
        }
    }

    fun startUsernameInvestigation(username: String) {
        if (_scanProgress.value.isScanning) return
        viewModelScope.launch {
            _scanProgress.value = ScanProgressState(isScanning = true, target = username, step = "Probing 20+ public developer communities...", progress = 0.05f)
            val invId = repository.executeUsernameScan(username) { cur, tot, plat ->
                val pct = cur.toFloat() / tot.coerceAtLeast(1)
                _scanProgress.value = _scanProgress.value.copy(
                    step = "Checking $plat ($cur/$tot)",
                    progress = pct,
                    details = "Platform: $plat"
                )
            }
            _scanProgress.value = ScanProgressState(isScanning = false, completedInvestigationId = invId)
            selectInvestigation(invId)
        }
    }

    fun startEmailInvestigation(email: String) {
        if (_scanProgress.value.isScanning) return
        viewModelScope.launch {
            _scanProgress.value = ScanProgressState(isScanning = true, target = email, step = "Validating RFC syntax...", progress = 0.10f)
            val invId = repository.executeEmailScan(email) { step, pct ->
                _scanProgress.value = _scanProgress.value.copy(step = step, progress = pct)
            }
            _scanProgress.value = ScanProgressState(isScanning = false, completedInvestigationId = invId)
            selectInvestigation(invId)
        }
    }

    fun startSpiderFootScan(target: String, targetType: String) {
        if (_scanProgress.value.isScanning) return
        viewModelScope.launch {
            _scanProgress.value = ScanProgressState(isScanning = true, target = target, step = "Connecting to SpiderFoot at ${_spiderFootDaemonUrl.value}...", progress = 0.10f)
            val invId = repository.executeSpiderFootScan(
                target = target,
                targetType = targetType,
                daemonUrl = _spiderFootDaemonUrl.value
            ) { step, pct, mod ->
                _scanProgress.value = _scanProgress.value.copy(step = step, progress = pct, details = "Module: $mod")
            }
            _scanProgress.value = ScanProgressState(isScanning = false, completedInvestigationId = invId)
            selectInvestigation(invId)
        }
    }

    fun reportScam(phone: String, category: String, notes: String, isVerified: Boolean) {
        viewModelScope.launch {
            repository.submitScamReport(phone, category, notes, isVerified)
        }
    }

    fun deleteInvestigation(id: String) {
        viewModelScope.launch {
            repository.deleteInvestigation(id)
            if (_activeInvestigationId.value == id) {
                _activeInvestigationId.value = null
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _activeInvestigationId.value = null
        }
    }
}
