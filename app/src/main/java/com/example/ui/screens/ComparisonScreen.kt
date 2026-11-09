package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.InvestigationEntity
import com.example.ui.NumberScopeViewModel
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun ComparisonScreen(
    viewModel: NumberScopeViewModel,
    modifier: Modifier = Modifier
) {
    val allInvestigations by viewModel.allInvestigations.collectAsStateWithLifecycle()
    var selectedId1 by remember { mutableStateOf(allInvestigations.getOrNull(0)?.id) }
    var selectedId2 by remember { mutableStateOf(allInvestigations.getOrNull(1)?.id) }

    val inv1 = allInvestigations.find { it.id == selectedId1 }
    val inv2 = allInvestigations.find { it.id == selectedId2 }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "OSINT CROSS-CORRELATION",
                color = CyberViolet,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "Compare Separate Scans",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Analyze risk variance, target discrepancies, and footprint shifts across two investigation sessions.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Selection Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InvestigationPicker(
                    label = "SCAN A",
                    selected = inv1,
                    all = allInvestigations,
                    onSelect = { selectedId1 = it.id },
                    modifier = Modifier.weight(1f)
                )

                InvestigationPicker(
                    label = "SCAN B",
                    selected = inv2,
                    all = allInvestigations,
                    onSelect = { selectedId2 = it.id },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (inv1 != null && inv2 != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SIDE-BY-SIDE PARAMETER MATRIX",
                            color = ElectricBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        CompareRow(label = "Target", val1 = inv1.target, val2 = inv2.target, isMonospace = true)
                        CompareRow(label = "Type", val1 = inv1.type, val2 = inv2.type)
                        CompareRow(label = "Scanner Engine", val1 = inv1.scanner, val2 = inv2.scanner)
                        CompareRow(label = "Status", val1 = inv1.status, val2 = inv2.status)
                        CompareRow(label = "Findings Gathered", val1 = "${inv1.findingsCount} items", val2 = "${inv2.findingsCount} items")
                        CompareRow(label = "Risk Score", val1 = "${inv1.riskScore}/100", val2 = "${inv2.riskScore}/100")
                        CompareRow(label = "Risk Level", val1 = inv1.riskLevel, val2 = inv2.riskLevel)
                        CompareRow(label = "Duration", val1 = "${inv1.durationMs} ms", val2 = "${inv2.durationMs} ms")
                    }
                }
            }

            // Delta Summary
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberCardBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "DELTA OBSERVATIONS",
                            color = CyberViolet,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val scoreDiff = inv2.riskScore - inv1.riskScore
                        val scoreText = if (scoreDiff > 0) "+$scoreDiff risk points higher in Scan B"
                        else if (scoreDiff < 0) "$scoreDiff risk points lower in Scan B"
                        else "Identical risk scores (no variance)"

                        Text(
                            text = "• Risk Variance: $scoreText",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "• Findings Count Delta: ${inv2.findingsCount - inv1.findingsCount} items difference",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "• Telemetry Alignment: ${if (inv1.type == inv2.type) "Same investigation vector (${inv1.type})" else "Different OSINT modalities (${inv1.type} vs ${inv2.type})"}",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCard)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Select two investigations above to compare telemetry side-by-side.", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun InvestigationPicker(
    label: String,
    selected: InvestigationEntity?,
    all: List<InvestigationEntity>,
    onSelect: (InvestigationEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = modifier.clickable { expanded = true },
        colors = CardDefaults.outlinedCardColors(containerColor = CyberSurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, color = ElectricBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selected?.target ?: "Tap to choose scan",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (selected != null) "${selected.type} • ${selected.id}" else "None",
                color = TextTertiary,
                fontSize = 10.sp
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                all.forEach { inv ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(inv.target, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${inv.type} (${inv.id})", fontSize = 10.sp, color = TextTertiary)
                            }
                        },
                        onClick = {
                            onSelect(inv)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CompareRow(
    label: String,
    val1: String,
    val2: String,
    isMonospace: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, color = TextTertiary, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = val1,
                color = TextPrimary,
                fontSize = 12.sp,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = val2,
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
