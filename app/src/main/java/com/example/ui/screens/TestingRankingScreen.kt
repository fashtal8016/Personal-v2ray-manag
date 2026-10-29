package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.components.LatencyChip
import com.example.ui.components.ProtocolBadge
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DeepNeonBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.PinkAccent
import com.example.ui.theme.RedDanger
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.YellowWarning
import com.example.util.FormatUtils

@Composable
fun TestingRankingScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val mainSubServers by viewModel.mainSubServers.collectAsStateWithLifecycle()
    val allStats by viewModel.allStats.collectAsStateWithLifecycle()
    val statsMap = allStats.associateBy { it.serverId }

    var showFormulaDetails by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
    ) {
        // Screen Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column {
                Text(
                    text = "REAL TESTING & RANKING",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Automated top-50 node selection for 'main sub'",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .testTag("formula_info_btn"),
                onClick = { showFormulaDetails = !showFormulaDetails }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Formula", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Formula Explanation Expandable Banner
        AnimatedVisibility(visible = showFormulaDetails) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Deterministic Ranking Formula",
                        color = CyanAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Reliability (40%): Ratio of successful handshakes & probes\n" +
                                "• Latency (25%): Normalized TCP connect & TLS handshake\n" +
                                "• Download Speed (25%): Throughput bandwidth measurement\n" +
                                "• Latency Consistency (10%): Low variation & jitter\n" +
                                "• Top 50 qualifying nodes are atomically persisted to 'main sub'.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Test Engine Status & Action Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (syncProgress.isRunning) CyanAccent else DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (syncProgress.isRunning) {
                            CircularProgressIndicator(
                                color = CyanAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = syncProgress.stage,
                            color = if (syncProgress.isRunning) CyanAccent else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (syncProgress.isRunning) {
                        OutlinedButton(
                            onClick = { viewModel.cancelSync() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedDanger),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RedDanger.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp).testTag("cancel_sync_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Cancel", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar
                if (syncProgress.totalNodesFound > 0) {
                    val progressFraction = (syncProgress.nodesTested.toFloat() / syncProgress.totalNodesFound.toFloat())
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = CyanAccent,
                        trackColor = DarkSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Batch Counters Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CounterBadge("Total", syncProgress.totalNodesFound.toString(), TextPrimary)
                    CounterBadge("Tested", syncProgress.nodesTested.toString(), ElectricBlue)
                    CounterBadge("Working", syncProgress.nodesPassed.toString(), GreenSuccess)
                    CounterBadge("Failed", syncProgress.nodesFailed.toString(), RedDanger)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Start Button
                Button(
                    onClick = { viewModel.startMainSubSync() },
                    enabled = !syncProgress.isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = DarkBg
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("start_sync_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (syncProgress.isRunning) "Running Test Suite..." else "Run Test Suite & Update 'main sub'",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // "main sub" Top 50 Nodes Table Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "MAIN SUB (TOP 50)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "${mainSubServers.size} / 50 nodes",
                color = CyanAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // List of Ranked Top 50 Nodes
        if (mainSubServers.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "'main sub' is currently empty",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap 'Run Test Suite' above to test and rank the best 50 nodes",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(mainSubServers, key = { _, s -> s.id }) { index, server ->
                    val stats = statsMap[server.id]
                    RankedServerRow(
                        rank = index + 1,
                        name = server.name,
                        protocol = server.protocol.displayName,
                        latencyMs = stats?.avgLatencyMs ?: 0L,
                        speedBps = stats?.avgDownloadSpeedBps ?: 0L,
                        reliability = stats?.reliability ?: 0f,
                        score = stats?.compositeScore ?: 0f
                    )
                }
            }
        }
    }
}

@Composable
fun CounterBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
fun RankedServerRow(
    rank: Int,
    name: String,
    protocol: String,
    latencyMs: Long,
    speedBps: Long,
    reliability: Float,
    score: Float
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (rank <= 3) CyanAccent.copy(alpha = 0.5f) else DarkBorder),
        modifier = Modifier.fillMaxWidth().testTag("ranked_row_$rank")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Rank Number Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (rank <= 3) CyanAccent.copy(alpha = 0.2f) else DarkSurfaceVariant)
                ) {
                    Text(
                        text = "#$rank",
                        color = if (rank <= 3) CyanAccent else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$protocol • Rel: ${(reliability * 100).toInt()}%${if (speedBps > 0) " • " + FormatUtils.formatSpeed(speedBps) else ""}",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            // Latency & Score Badge
            Column(horizontalAlignment = Alignment.End) {
                if (latencyMs > 0) {
                    Text(
                        text = "${latencyMs} ms",
                        color = if (latencyMs < 200) GreenSuccess else YellowWarning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (score > 0) {
                    Text(
                        text = "Score ${score.toInt()}",
                        color = ElectricBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
