package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProxyProtocol
import com.example.model.ServerProfile
import com.example.model.ServerStatsEntity
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.PinkAccent
import com.example.ui.theme.PurpleNeon
import com.example.ui.theme.RedDanger
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.YellowWarning
import com.example.util.FormatUtils

@Composable
fun ProtocolBadge(protocol: ProxyProtocol, modifier: Modifier = Modifier) {
    val (color, bgColor) = when (protocol) {
        ProxyProtocol.VLESS -> ElectricBlue to ElectricBlue.copy(alpha = 0.15f)
        ProxyProtocol.VMESS -> PurpleNeon to PurpleNeon.copy(alpha = 0.15f)
        ProxyProtocol.TROJAN -> YellowWarning to YellowWarning.copy(alpha = 0.15f)
        ProxyProtocol.SHADOWSOCKS -> CyanAccent to CyanAccent.copy(alpha = 0.15f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = protocol.displayName,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MainSubTag(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CyanAccent.copy(alpha = 0.15f))
            .border(1.dp, CyanAccent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "main sub",
                color = CyanAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun LatencyChip(stats: ServerStatsEntity?, modifier: Modifier = Modifier) {
    if (stats == null || stats.totalTests == 0) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceVariant)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(text = "untested", color = TextSecondary, fontSize = 11.sp)
        }
        return
    }

    if (!stats.lastStatusSuccess || stats.avgLatencyMs <= 0) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(RedDanger.copy(alpha = 0.15f))
                .border(1.dp, RedDanger.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(text = "failed", color = RedDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    val latency = stats.avgLatencyMs
    val (color, bg) = when {
        latency < 180 -> GreenSuccess to GreenSuccess.copy(alpha = 0.15f)
        latency < 450 -> YellowWarning to YellowWarning.copy(alpha = 0.15f)
        else -> RedDanger to RedDanger.copy(alpha = 0.15f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = "${latency} ms",
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ServerItemCard(
    profile: ServerProfile,
    stats: ServerStatsEntity?,
    isMainSub: Boolean,
    isSelected: Boolean,
    isTesting: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) CyanAccent else DarkBorder
    val surfaceColor = if (isSelected) DarkSurfaceVariant else DarkSurface

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = surfaceColor,
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("server_card_${profile.id}")
            .clickable { onSelect() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Protocol, Main Sub tag, Favorite, Inspect
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProtocolBadge(protocol = profile.protocol)
                    if (isMainSub) {
                        MainSubTag()
                    }
                    if (profile.security.name != "NONE") {
                        Text(
                            text = profile.security.value.uppercase(),
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Inspect Details Button
                    IconButton(
                        onClick = onInspect,
                        modifier = Modifier.size(32.dp).testTag("inspect_btn_${profile.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Inspect Node",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Favorite Toggle Button
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(32.dp).testTag("favorite_btn_${profile.id}")
                    ) {
                        Icon(
                            imageVector = if (profile.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Toggle Favorite",
                            tint = if (profile.isFavorite) YellowWarning else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Server Name
            Text(
                text = profile.name,
                color = if (isSelected) CyanAccent else TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Endpoint & Transport info
            Text(
                text = "${profile.server}:${profile.port} • ${profile.transport.value.uppercase()}${if (profile.path.isNotEmpty()) " (${profile.path})" else ""}",
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Footer: Latency, Speed, Ping Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LatencyChip(stats = stats)

                    if (stats != null && stats.avgDownloadSpeedBps > 0) {
                        Text(
                            text = FormatUtils.formatSpeed(stats.avgDownloadSpeedBps),
                            color = ElectricBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (stats != null && stats.compositeScore > 0f) {
                        Text(
                            text = "Score: ${stats.compositeScore.toInt()}",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                // Individual Ping Test Button
                IconButton(
                    onClick = onTest,
                    enabled = !isTesting,
                    modifier = Modifier.size(32.dp).testTag("test_ping_btn_${profile.id}")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            color = CyanAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Test Ping",
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
