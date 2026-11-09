package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.NumberScopeViewModel
import com.example.ui.Screen
import com.example.ui.components.InvestigationItemCard
import com.example.ui.components.ScanActiveBanner
import com.example.ui.components.StatMetricCard
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun DashboardScreen(
    viewModel: NumberScopeViewModel,
    modifier: Modifier = Modifier
) {
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val completedCount by viewModel.completedCount.collectAsStateWithLifecycle()
    val failedCount by viewModel.failedCount.collectAsStateWithLifecycle()
    val runningCount by viewModel.runningCount.collectAsStateWithLifecycle()
    val recentInvestigations by viewModel.recentInvestigations.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val phoneInfogaStatus by viewModel.phoneInfogaStatus.collectAsStateWithLifecycle()
    val spiderFootStatus by viewModel.spiderFootStatus.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Live Scan Banner if active
            if (scanProgress.isScanning) {
                ScanActiveBanner(
                    target = scanProgress.target,
                    step = scanProgress.step,
                    progress = scanProgress.progress,
                    details = scanProgress.details
                )
            }
        }

        // Metrics Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetricCard(
                        title = "Total Scans",
                        value = totalCount.toString(),
                        icon = Icons.Default.Security,
                        accentColor = ElectricBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Completed",
                        value = completedCount.toString(),
                        icon = Icons.Default.CheckCircle,
                        accentColor = CyberGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetricCard(
                        title = "Running",
                        value = runningCount.toString(),
                        icon = Icons.Default.Sync,
                        accentColor = CyberAmber,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Failed / Alert",
                        value = failedCount.toString(),
                        icon = Icons.Default.ReportProblem,
                        accentColor = CyberRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Quick Launch Investigation Modules
        item {
            Text(
                text = "INVESTIGATION SUITE",
                color = ElectricBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModuleQuickTile(
                    title = "Phone Intel",
                    subtitle = "PhoneInfoga E.164",
                    icon = Icons.Default.Phone,
                    color = ElectricBlue,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setScreen(Screen.PHONE_INTEL) }
                )
                ModuleQuickTile(
                    title = "SpiderFoot",
                    subtitle = "Multi-Source OSINT",
                    icon = Icons.Default.Language,
                    color = CyberViolet,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setScreen(Screen.SPIDERFOOT) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModuleQuickTile(
                    title = "Username",
                    subtitle = "30+ Web Checks",
                    icon = Icons.Default.PersonSearch,
                    color = CyberGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setScreen(Screen.USERNAME_INTEL) }
                )
                ModuleQuickTile(
                    title = "Email OSINT",
                    subtitle = "RFC / DNS / MX",
                    icon = Icons.Default.AlternateEmail,
                    color = CyberAmber,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setScreen(Screen.EMAIL_INTEL) }
                )
            }
        }

        // Real Scanner Integrations Availability Status
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SCANNER ENGINE STATUS",
                            color = ElectricBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Configure in Settings",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { viewModel.setScreen(Screen.SETTINGS) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // PhoneInfoga status row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isPiOnline = phoneInfogaStatus.startsWith("ONLINE", ignoreCase = true) || phoneInfogaStatus.startsWith("CLI READY", ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isPiOnline) CyberGreen else CyberAmber)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "PhoneInfoga: ${if (isPiOnline) "READY" else "OFFLINE"}",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = phoneInfogaStatus,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // SpiderFoot status row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isSfOnline = spiderFootStatus.startsWith("ONLINE", ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isSfOnline) CyberGreen else CyberAmber)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SpiderFoot: ${if (isSfOnline) "ONLINE" else "OFFLINE"}",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = spiderFootStatus,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Recent Investigations Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT INVESTIGATIONS",
                    color = ElectricBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "View All",
                    color = ElectricBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { viewModel.setScreen(Screen.HISTORY) }
                        .padding(4.dp)
                )
            }
        }

        if (recentInvestigations.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No investigations logged yet",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Launch a Phone, SpiderFoot, or Username scan above to begin.",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(recentInvestigations, key = { it.id }) { inv ->
                InvestigationItemCard(
                    investigation = inv,
                    onClick = { viewModel.selectInvestigation(inv.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ModuleQuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("module_tile_${title.replace(" ", "_").lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
