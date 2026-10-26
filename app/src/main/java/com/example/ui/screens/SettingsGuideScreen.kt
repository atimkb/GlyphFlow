package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.AuditStatus
import com.example.domain.model.ConditionType
import com.example.domain.model.MediaTriggerState
import com.example.hardware.HardwareMatrixState
import com.example.ui.components.GlyphMatrix25Canvas
import com.example.ui.components.NothingCompactPill
import com.example.ui.components.NothingPillBadge
import com.example.ui.theme.NothingBorderHairline
import com.example.ui.theme.NothingCarbonSurface
import com.example.ui.theme.NothingCrimsonRed
import com.example.ui.theme.NothingElevatedSurface
import com.example.ui.theme.NothingMatrixGreen
import com.example.ui.theme.NothingMutedGray
import com.example.ui.theme.NothingPureBlack
import com.example.ui.theme.NothingPureWhite
import com.example.ui.theme.NothingSignalAmber
import com.example.ui.theme.NothingTechnicalGray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tab 3: Settings & Guide
 * - Hardware Validation & 3-Step Self-Audit (Outer Ring -> Radial Core Sweep -> 100% Brightness Flash).
 * - Permission Status Cards with direct action buttons (Notification Access, Battery Unrestricted, Background Daemon).
 * - Visual User Guide with step-by-step interactive cards.
 * - Workspace Offline JSON Backup & Collapsible Signal Diagnostics / Simulator.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsGuideScreen(
    hardwareState: HardwareMatrixState,
    isNotificationListenerConnected: Boolean,
    isBatteryUnrestricted: Boolean,
    isForegroundServiceRunning: Boolean,
    auditLogs: List<AuditLogEntry>,
    onRunHardwareSelfTest: () -> Unit,
    onVerifyPhysicalToggle: () -> Unit = {},
    onStopMatrixSession: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onToggleForegroundService: () -> Unit,
    onOpenJsonBackupModal: () -> Unit,
    onClearAuditLogs: () -> Unit,
    onSimulateNotification: (pkg: String, label: String, title: String, content: String, progress: Int?) -> Unit,
    onSimulateMedia: (MediaTriggerState, String) -> Unit,
    onSimulateBattery: (level: Int, isCharging: Boolean, triggerChargingEvent: Boolean) -> Unit,
    onSimulatePosture: (ConditionType) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDiagnosticsAccordion by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NothingPureBlack)
            .testTag("settings_guide_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hardware Validation & 3-Step Self-Audit
        item {
            HardwareSelfAuditCard(
                hardwareState = hardwareState,
                onRunSelfTest = onRunHardwareSelfTest,
                onVerifyPhysicalToggle = onVerifyPhysicalToggle,
                onStopMatrix = onStopMatrixSession
            )
        }

        // 2. Permission Status Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsSectionHeader(
                    title = "PERMISSIONS & BACKGROUND RELIABILITY",
                    subtitle = "Ensure uninterrupted 30 FPS matrix streaming on Nothing OS"
                )

                PermissionStatusCard(
                    title = "Notification Access",
                    subtitle = if (isNotificationListenerConnected) {
                        "Connected — Monitoring app notifications & ongoing media sessions"
                    } else {
                        "Required to trigger Glyph rules from WhatsApp, Maps, Spotify & timers"
                    },
                    statusLabel = if (isNotificationListenerConnected) "GRANTED" else "REQUIRED",
                    isGranted = isNotificationListenerConnected,
                    actionButtonLabel = if (isNotificationListenerConnected) "CONFIGURE" else "GRANT ACCESS",
                    icon = Icons.Default.NotificationsActive,
                    onClick = onOpenNotificationAccess,
                    testTag = "permission_card_notifications"
                )

                PermissionStatusCard(
                    title = "Battery Unrestricted",
                    subtitle = if (isBatteryUnrestricted) {
                        "Unrestricted — Nothing OS will not suspend continuous matrix animations"
                    } else {
                        "Recommended so long-running Glyph animations never time out in background"
                    },
                    statusLabel = if (isBatteryUnrestricted) "UNRESTRICTED" else "OPTIMIZED",
                    isGranted = isBatteryUnrestricted,
                    actionButtonLabel = if (isBatteryUnrestricted) "SETTINGS" else "ALLOW UNRESTRICTED",
                    icon = Icons.Default.BatteryChargingFull,
                    onClick = onOpenBatterySettings,
                    testTag = "permission_card_battery"
                )

                PermissionStatusCard(
                    title = "Persistent Glyph Daemon",
                    subtitle = if (isForegroundServiceRunning) {
                        "Active — Foreground service keeping hardware session & audio monitor warm"
                    } else {
                        "Start the foreground daemon to keep rules active when the screen is off"
                    },
                    statusLabel = if (isForegroundServiceRunning) "ACTIVE" else "STANDBY",
                    isGranted = isForegroundServiceRunning,
                    actionButtonLabel = if (isForegroundServiceRunning) "STOP DAEMON" else "START DAEMON",
                    icon = Icons.Default.PowerSettingsNew,
                    onClick = onToggleForegroundService,
                    testTag = "permission_card_daemon"
                )
            }
        }

        // 3. Visual User Guide (Step-by-Step Cards)
        item {
            VisualUserGuideSection()
        }

        // 4. Offline Workspace Backup & Signal Simulator Accordion
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NothingCarbonSurface,
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NothingBorderHairline, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "WORKSPACE JSON BACKUP",
                                style = MaterialTheme.typography.titleMedium,
                                color = NothingPureWhite
                            )
                            Text(
                                text = "Export or import all 25×25 circular animations and automation rules offline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTechnicalGray
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenJsonBackupModal,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_json_backup_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = NothingPureWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "EXPORT / IMPORT WORKSPACE JSON",
                                style = MaterialTheme.typography.labelMedium,
                                color = NothingPureWhite
                            )
                        }
                    }

                    HorizontalDivider(color = NothingBorderHairline)

                    // Collapsible Signal Simulator & Hardware Audit Log
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showDiagnosticsAccordion = !showDiagnosticsAccordion }
                            .padding(vertical = 6.dp)
                            .testTag("toggle_diagnostics_accordion"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = NothingCrimsonRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "SIGNAL SIMULATOR & AUDIT LOG (${auditLogs.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NothingPureWhite
                                )
                                Text(
                                    text = "Test-fire synthetic notifications, audio playback, or battery events",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NothingTechnicalGray
                                )
                            }
                        }
                        Icon(
                            imageVector = if (showDiagnosticsAccordion) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand Diagnostics",
                            tint = NothingTechnicalGray
                        )
                    }

                    AnimatedVisibility(visible = showDiagnosticsAccordion) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SimulatorQuickChip(
                                    label = "WhatsApp VIP Msg",
                                    onClick = {
                                        onSimulateNotification(
                                            "com.whatsapp",
                                            "WhatsApp",
                                            "urgent launch update",
                                            "Prototype ready for review",
                                            null
                                        )
                                    },
                                    tag = "sim_whatsapp"
                                )
                                SimulatorQuickChip(
                                    label = "Play Music (Audio Shader)",
                                    onClick = {
                                        onSimulateMedia(MediaTriggerState.PLAYING, "Nothing Synthwave 01")
                                    },
                                    tag = "sim_media_play"
                                )
                                SimulatorQuickChip(
                                    label = "Stop Music (Clear Matrix)",
                                    onClick = {
                                        onSimulateMedia(MediaTriggerState.PAUSED, "Nothing Synthwave 01")
                                    },
                                    tag = "sim_media_pause"
                                )
                                SimulatorQuickChip(
                                    label = "Maps Progress 68%",
                                    onClick = {
                                        onSimulateNotification(
                                            "com.google.android.apps.maps",
                                            "Google Maps",
                                            "Turn right in 200m",
                                            "6 min remaining",
                                            68
                                        )
                                    },
                                    tag = "sim_maps"
                                )
                                SimulatorQuickChip(
                                    label = "Battery 15% Alert",
                                    onClick = {
                                        onSimulateBattery(15, false, false)
                                    },
                                    tag = "sim_battery_low"
                                )
                                SimulatorQuickChip(
                                    label = "Flip to Glyph (Face Down)",
                                    onClick = {
                                        onSimulatePosture(ConditionType.FACE_DOWN)
                                    },
                                    tag = "sim_face_down"
                                )
                            }

                            if (auditLogs.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RECENT ENGINE TELEMETRY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NothingTechnicalGray
                                    )
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(onClick = onClearAuditLogs)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = "Clear logs",
                                            tint = NothingTechnicalGray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "CLEAR",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NothingTechnicalGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDiagnosticsAccordion && auditLogs.isNotEmpty()) {
            items(auditLogs.take(15), key = { it.id }) { entry ->
                CompactAuditLogItem(entry = entry)
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = NothingPureWhite
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = NothingTechnicalGray
        )
    }
}

@Composable
private fun HardwareSelfAuditCard(
    hardwareState: HardwareMatrixState,
    onRunSelfTest: () -> Unit,
    onVerifyPhysicalToggle: () -> Unit,
    onStopMatrix: () -> Unit
) {
    val isActive = hardwareState.isSelfAuditRunning || hardwareState.isSessionOpen || hardwareState.activeDotCount > 0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NothingCarbonSurface,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isActive || !hardwareState.lastHardwareError.isNullOrBlank()) {
                        NothingCrimsonRed
                    } else {
                        NothingBorderHairline
                    },
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = NothingCrimsonRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "HARDWARE SELF-AUDIT (DEVICE_23112)",
                            style = MaterialTheme.typography.titleMedium,
                            color = NothingPureWhite
                        )
                        Text(
                            text = hardwareState.handshakeStatusLog,
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTechnicalGray
                        )
                    }
                }

                NothingPillBadge(
                    text = when {
                        hardwareState.isSelfAuditRunning -> "SELF-TEST"
                        hardwareState.activeFps > 0 -> "${hardwareState.activeFps} FPS"
                        hardwareState.isServiceBound -> "BOUND"
                        else -> "UNBOUND"
                    },
                    color = if (isActive || !hardwareState.isServiceBound) NothingCrimsonRed else NothingPureWhite
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlyphMatrix25Canvas(
                    pixels = hardwareState.currentPixels,
                    modifier = Modifier
                        .size(116.dp)
                        .testTag("self_audit_matrix_preview"),
                    interactive = false,
                    showCircularBezelOverlay = true
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "3-STEP DIAGNOSTIC SEQUENCE",
                        style = MaterialTheme.typography.labelMedium,
                        color = NothingCrimsonRed
                    )
                    Text(
                        text = "1. Outer Perimeter Ring\n2. Radial Core Sweep\n3. 100% Brightness Flash",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingPureWhite
                    )
                    Text(
                        text = hardwareState.selfAuditStatus.ifBlank {
                            "Active dots: ${hardwareState.activeDotCount}/497 • Sessions: ${hardwareState.sessionCount}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTechnicalGray
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRunSelfTest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingCrimsonRed,
                        contentColor = NothingPureWhite
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("self_test_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hardwareState.isSelfAuditRunning) "RUNNING..." else "RUN 3-STEP SELF-TEST",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (isActive) {
                    OutlinedButton(
                        onClick = onStopMatrix,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("clear_matrix_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Matrix",
                            tint = NothingPureWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "OFF",
                            style = MaterialTheme.typography.labelMedium,
                            color = NothingPureWhite
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onVerifyPhysicalToggle,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_verify_physical_button")
            ) {
                Text(
                    text = "VERIFY PHYSICAL 625 RAW BUFFER (setMatrixFrame)",
                    style = MaterialTheme.typography.labelMedium,
                    color = NothingPureWhite
                )
            }

            if (!hardwareState.lastHardwareError.isNullOrBlank()) {
                Text(
                    text = hardwareState.lastHardwareError,
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingCrimsonRed,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_hardware_error_red_text")
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    title: String,
    subtitle: String,
    statusLabel: String,
    isGranted: Boolean,
    actionButtonLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        color = NothingCarbonSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NothingBorderHairline, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isGranted) NothingMatrixGreen.copy(alpha = 0.14f)
                                else NothingSignalAmber.copy(alpha = 0.14f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isGranted) NothingMatrixGreen.copy(alpha = 0.4f) else NothingSignalAmber.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                            contentDescription = null,
                            tint = if (isGranted) NothingMatrixGreen else NothingSignalAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NothingPureWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                NothingPillBadge(
                    text = statusLabel,
                    color = if (isGranted) NothingMatrixGreen else NothingSignalAmber
                )
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NothingTechnicalGray,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = actionButtonLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = NothingPureWhite
                )
            }
        }
    }
}

@Composable
private fun VisualUserGuideSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionHeader(
            title = "VISUAL USER GUIDE",
            subtitle = "How to create circular 25×25 Glyphs & persistent automations"
        )

        GuideStepCard(
            stepBadge = "STEP 01",
            title = "Circular Matrix Board & LIVE Hardware Mirror",
            description = "Open the STUDIO tab to paint directly on the circular 497-LED aperture. Toggle the red [LIVE] button to mirror every brushstroke and keyframe directly onto the back of your Nothing Phone (3) at 30 FPS."
        )

        GuideStepCard(
            stepBadge = "STEP 02",
            title = "AI Prompt-to-Glyph & 1:1 Photo Converter",
            description = "Inside STUDIO, type a prompt like 'right arrow', 'heartbeat pulse', or 'lightning bolt' to procedurally synthesize multi-frame animations, or pick any photo to crop 1:1, downscale to 25×25, and dither across 0–255 brightness."
        )

        GuideStepCard(
            stepBadge = "STEP 03",
            title = "Persistent 30 FPS Rules & Audio Waveform Shader",
            description = "Tap [+ Create Rule] on the Dashboard to link any installed app, battery threshold, or active music session to a saved preset or the real-time Audio Waveform Shader. Rules stream continuously until the event stops."
        )
    }
}

@Composable
private fun GuideStepCard(
    stepBadge: String,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NothingCarbonSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NothingBorderHairline, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NothingPillBadge(
                    text = stepBadge,
                    color = NothingCrimsonRed
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NothingPureWhite
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = NothingTechnicalGray
            )
        }
    }
}

@Composable
private fun SimulatorQuickChip(
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    NothingCompactPill(
        label = label,
        selected = false,
        onClick = onClick,
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun CompactAuditLogItem(entry: AuditLogEntry) {
    val timeText = remember(entry.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(entry.timestamp))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NothingCarbonSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NothingBorderHairline, RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingMutedGray
                    )
                    Text(
                        text = entry.sourceSummary,
                        style = MaterialTheme.typography.labelMedium,
                        color = NothingPureWhite
                    )
                }
                Text(
                    text = "${entry.matchedRuleName ?: "Unmatched"} • ${entry.actionFiredSummary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTechnicalGray
                )
            }
            NothingPillBadge(
                text = entry.status.badgeText,
                color = if (entry.status == AuditStatus.TRIGGERED) NothingCrimsonRed else NothingTechnicalGray
            )
        }
    }
}
