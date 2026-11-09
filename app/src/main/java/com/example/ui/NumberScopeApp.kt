package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EthicalConsentModal
import com.example.ui.screens.ComparisonScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EmailScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PhoneScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.screens.ScamIntelScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SpiderFootScreen
import com.example.ui.screens.UsernameScreen
import com.example.ui.theme.CyberBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NumberScopeTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberScopeApp(viewModel: NumberScopeViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val userConsentGiven by viewModel.userConsentGiven.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()

    // Handle back button on sub-screens
    if (currentScreen != Screen.DASHBOARD) {
        BackHandler {
            viewModel.setScreen(Screen.DASHBOARD)
        }
    }

    NumberScopeTheme(darkTheme = isDarkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = if (isDarkTheme) CyberBg else Color(0xFFF4F7FC),
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (scanProgress.isScanning) ElectricBlue else CyberGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "NUMBERSCOPE",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.2.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(ElectricBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "OSINT",
                                            color = ElectricBlue,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = currentScreen.title,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleTheme() },
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = ElectricBlue
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setScreen(Screen.SETTINGS) },
                            modifier = Modifier.testTag("settings_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (currentScreen == Screen.SETTINGS) ElectricBlue else TextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isDarkTheme) CyberSurface else Color.White
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.border(1.dp, CyberCardBorder.copy(alpha = 0.5f)),
                    containerColor = if (isDarkTheme) CyberSurface else Color.White,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = currentScreen == Screen.DASHBOARD,
                        onClick = { viewModel.setScreen(Screen.DASHBOARD) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Dashboard"
                            )
                        },
                        label = { Text("SOC", fontSize = 10.sp) },
                        colors = navItemColors()
                    )

                    NavigationBarItem(
                        selected = currentScreen == Screen.PHONE_INTEL,
                        onClick = { viewModel.setScreen(Screen.PHONE_INTEL) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Phone"
                            )
                        },
                        label = { Text("Phone", fontSize = 10.sp) },
                        colors = navItemColors()
                    )

                    NavigationBarItem(
                        selected = currentScreen == Screen.SPIDERFOOT,
                        onClick = { viewModel.setScreen(Screen.SPIDERFOOT) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "SpiderFoot"
                            )
                        },
                        label = { Text("SpiderFoot", fontSize = 10.sp) },
                        colors = navItemColors()
                    )

                    NavigationBarItem(
                        selected = currentScreen == Screen.SCAM_INTEL,
                        onClick = { viewModel.setScreen(Screen.SCAM_INTEL) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.ReportProblem,
                                contentDescription = "Scam"
                            )
                        },
                        label = { Text("Scam DB", fontSize = 10.sp) },
                        colors = navItemColors()
                    )

                    NavigationBarItem(
                        selected = currentScreen == Screen.HISTORY || currentScreen == Screen.REPORT_CENTER || currentScreen == Screen.COMPARISON,
                        onClick = { viewModel.setScreen(Screen.HISTORY) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History"
                            )
                        },
                        label = { Text("Archive", fontSize = 10.sp) },
                        colors = navItemColors()
                    )
                }
            }
        ) { paddingValues ->
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screenTransition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) { target ->
                when (target) {
                    Screen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                    Screen.PHONE_INTEL -> PhoneScreen(viewModel = viewModel)
                    Screen.SPIDERFOOT -> SpiderFootScreen(viewModel = viewModel)
                    Screen.USERNAME_INTEL -> UsernameScreen(viewModel = viewModel)
                    Screen.EMAIL_INTEL -> EmailScreen(viewModel = viewModel)
                    Screen.SCAM_INTEL -> ScamIntelScreen(viewModel = viewModel)
                    Screen.HISTORY -> HistoryScreen(viewModel = viewModel)
                    Screen.COMPARISON -> ComparisonScreen(viewModel = viewModel)
                    Screen.REPORT_CENTER -> ReportScreen(viewModel = viewModel)
                    Screen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }

        if (!userConsentGiven) {
            EthicalConsentModal(onAccept = { viewModel.acceptConsent() })
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = CyberBg,
    selectedTextColor = ElectricBlue,
    indicatorColor = ElectricBlue,
    unselectedIconColor = TextTertiary,
    unselectedTextColor = TextTertiary
)
