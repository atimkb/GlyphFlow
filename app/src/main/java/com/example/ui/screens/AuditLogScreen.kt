package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.AuditStatus
import com.example.domain.model.ConditionType
import com.example.domain.model.HardwareBinderAuditEntry
import com.example.domain.model.MediaTriggerState
import com.example.domain.model.NotificationPayloadAuditEntry
import com.example.domain.model.ScriptCompileAuditEntry
import com.example.engine.EventIngestBus
import com.example.hardware.HardwareMatrixState
import com.example.ui.components.NothingPillBadge
import com.example.ui.theme.NothingBorderHairline
import com.example.ui.theme.NothingCarbonSurface
import com.example.ui.theme.NothingCrimsonRed
import com.example.ui.theme.NothingMatrixGreen
import com.example.ui.theme.NothingPureBlack
import com.example.ui.theme.NothingPureWhite
import com.example.ui.theme.NothingSignalAmber
import com.example.ui.theme.NothingTechnicalGray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuditLogScreen(
    hardwareState: HardwareMatrixState,
    auditLogs: List<AuditLogEntry>,
    scriptCompileHistory: List<ScriptCompileAuditEntry> = emptyList(),
    hardwareBinderLogs: List<HardwareBinderAuditEntry> = emptyList(),
    payloadInspectorLogs: List<NotificationPayloadAuditEntry> = emptyList(),
    onClearLogs: () -> Unit,
    onExportDiagnosticsCopied: () -> Unit = {},
    onSimulateNotification: (pkg: String, label: String, title: String, content: String, progress: Int?) -> Unit,
    onSimulateBattery: (level: Int, isCharging: Boolean, triggerChargingEvent: Boolean) -> Unit,
    onSimulateMedia: (MediaTriggerState, String) -> Unit,
    onSimulatePosture: (ConditionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var simPackage by remember { mutableStateOf("com.google.android.apps.maps") }
    var simTitle by remember { mutableStateOf("Turn right in 200m") }
    var simContent by remember { mutableStateOf("200m to destination • ETA 4 mins") }
    var includeProgress by remember { mutableStateOf(true) }
    var simProgress by remember { mutableFloatStateOf(72f) }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NothingPureBlack)
            .testTag("audit_log_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Hardware Session Status & "Export Diagnostics" Action
        item {
            Surface(
                color = NothingCarbonSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NothingBorderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "HARDWARE SESSION STATUS",
                                style = MaterialTheme.typography.titleMedium,
                                color = NothingPureWhite
                            )
                            Text(
                                text = "Model Registration: A024 (DEVICE_23112) • Session #${hardwareState.sessionCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTechnicalGray
                            )
                        }
                        NothingPillBadge(
                            text = if (hardwareState.isServiceBound && hardwareState.isDeviceRegistered) {
                                "A024 REGISTERED"
                            } else {
                                "BRIDGE FALLBACK"
                            },
                            color = if (hardwareState.isServiceBound && hardwareState.isDeviceRegistered) {
                                NothingMatrixGreen
                            } else {
                                NothingSignalAmber
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        com.example.ui.components.CircularGlyphMatrixPreview(
                            pixels = hardwareState.currentPixels,
                            interactive = false,
                            showCircularBezelOverlay = true,
                            modifier = Modifier
                                .size(76.dp)
                                .testTag("audit_live_inspector_preview")
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                NothingPillBadge(
                                    text = "LATENCY: ${hardwareState.lastRenderLatencyMs}ms",
                                    color = NothingPureWhite
                                )
                                NothingPillBadge(
                                    text = "DOTS: ${hardwareState.activeDotCount}/489",
                                    color = NothingTechnicalGray
                                )
                                NothingPillBadge(
                                    text = "PEAK: ${hardwareState.peakBrightness}/255",
                                    color = NothingTechnicalGray
                                )
                            }

                            Text(
                                text = hardwareState.handshakeStatusLog,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = NothingTechnicalGray
                                )
                            )
                        }
                    }

                    if (hardwareBinderLogs.isNotEmpty()) {
                        HorizontalDivider(color = NothingBorderHairline)
                        Text(
                            text = "RECENT IGlyphService BINDER TRANSMISSIONS (${hardwareBinderLogs.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingCrimsonRed
                        )
                        hardwareBinderLogs.take(3).forEach { binderEntry ->
                            Text(
                                text = "[${timeFormatter.format(Date(binderEntry.timestamp))}] ${binderEntry.binderMethod} • NonZero=${binderEntry.nonZeroPixelCount}/625 • ${binderEntry.latencyMs}ms",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = NothingPureWhite
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val hwSummary = "Bound=${hardwareState.isServiceBound}, Registered(A024)=${hardwareState.isDeviceRegistered}, " +
                                    "SessionOpen=${hardwareState.isSessionOpen}, Sessions=${hardwareState.sessionCount}, " +
                                    "Latency=${hardwareState.lastRenderLatencyMs}ms, ActiveDots=${hardwareState.activeDotCount}/497, " +
                                    "Handshake=\"${hardwareState.handshakeStatusLog}\""
                                val fullReport = EventIngestBus.buildFullDiagnosticsExportText(hwSummary)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("GlyphFlow Diagnostics", fullReport)
                                )
                                onExportDiagnosticsCopied()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingCrimsonRed,
                                contentColor = NothingPureWhite
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_diagnostics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "EXPORT DIAGNOSTICS",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = onClearLogs,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, NothingBorderHairline),
                            modifier = Modifier.testTag("clear_audit_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Logs",
                                tint = NothingTechnicalGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CLEAR",
                                style = MaterialTheme.typography.labelMedium,
                                color = NothingTechnicalGray
                            )
                        }
                    }
                }
            }
        }

        // 2. Script Parse & Package Resolution History
        item {
            Surface(
                color = NothingCarbonSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NothingBorderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SCRIPT PARSE HISTORY (${scriptCompileHistory.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = NothingPureWhite
                        )
                        NothingPillBadge(
                            text = "GLYPHSCRIPT V2",
                            color = NothingTechnicalGray
                        )
                    }

                    if (scriptCompileHistory.isEmpty()) {
                        Text(
                            text = "No GlyphScript v2 compilation events logged yet. Run [Dry Run] or [Import & Apply] in the Console tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTechnicalGray
                        )
                    } else {
                        scriptCompileHistory.take(6).forEach { item ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NothingPureBlack)
                                    .border(
                                        1.dp,
                                        if (item.isSuccess) NothingBorderHairline else NothingCrimsonRed,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NothingPillBadge(
                                            text = item.astStatus,
                                            color = if (item.isSuccess) NothingMatrixGreen else NothingCrimsonRed
                                        )
                                        Text(
                                            text = timeFormatter.format(Date(item.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NothingTechnicalGray
                                        )
                                    }
                                    Text(
                                        text = "Rule: \"${item.ruleName}\" • Pkg: ${item.packageResolutionOutcome}",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = NothingPureWhite
                                        )
                                    )
                                    if (!item.errorMessage.isNullOrBlank()) {
                                        Text(
                                            text = item.errorMessage,
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = NothingCrimsonRed
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Live Payload Inspector (EXTRA_TITLE, EXTRA_TEXT, EXTRA_SUB_TEXT, EXTRA_PROGRESS & Regex Extraction)
        item {
            Surface(
                color = NothingCarbonSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NothingBorderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PAYLOAD INSPECTOR (${payloadInspectorLogs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = NothingPureWhite
                        )
                        NothingPillBadge(
                            text = "BUNDLE EXTRAS",
                            color = NothingCrimsonRed
                        )
                    }

                    if (payloadInspectorLogs.isEmpty()) {
                        Text(
                            text = "No notification payloads captured yet. Incoming system notifications or injected test signals will display raw Bundle extras here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTechnicalGray
                        )
                    } else {
                        payloadInspectorLogs.take(5).forEach { payload ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NothingPureBlack)
                                    .border(1.dp, NothingBorderHairline, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${payload.appLabel} (${payload.packageName})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NothingPureWhite
                                        )
                                        Text(
                                            text = timeFormatter.format(Date(payload.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NothingTechnicalGray
                                        )
                                    }
                                    Text(
                                        text = "EXTRA_TITLE: \"${payload.rawExtraTitle}\"\n" +
                                            "EXTRA_TEXT: \"${payload.rawExtraText}\"\n" +
                                            "EXTRA_SUB_TEXT: \"${payload.rawExtraSubText}\"\n" +
                                            "EXTRA_PROGRESS: ${payload.rawExtraProgress ?: "null"}/${payload.rawExtraProgressMax ?: "null"} (${payload.computedProgressPercent ?: 0}%)",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = NothingTechnicalGray
                                        )
                                    )
                                    if (payload.extractedVariablesSummary.isNotBlank()) {
                                        Text(
                                            text = "EXTRACT -> ${payload.extractedVariablesSummary}",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (payload.extractedVariablesSummary.contains("EXTRACT_FALLBACK"))
                                                    NothingSignalAmber
                                                else
                                                    NothingMatrixGreen
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val fallbackWarnings = EventIngestBus.extractFallbackWarnings.value
                    if (fallbackWarnings.isNotEmpty()) {
                        fallbackWarnings.take(4).forEach { warning ->
                            Text(
                                text = warning,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NothingSignalAmber
                                ),
                                modifier = Modifier.testTag("extract_fallback_warning_text")
                            )
                        }
                    }
                }
            }
        }

        // 4. Interactive Signal Injector (Simulator)
        item {
            Surface(
                color = NothingCarbonSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NothingBorderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = NothingCrimsonRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SIGNAL INJECTOR",
                                style = MaterialTheme.typography.titleMedium,
                                color = NothingPureWhite
                            )
                        }
                        NothingPillBadge(
                            text = "TEST BUS",
                            color = NothingTechnicalGray
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = simPackage,
                            onValueChange = { simPackage = it },
                            label = { Text("PACKAGE") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NothingCrimsonRed,
                                unfocusedBorderColor = NothingBorderHairline,
                                focusedTextColor = NothingPureWhite,
                                unfocusedTextColor = NothingPureWhite
                            )
                        )
                        OutlinedTextField(
                            value = simTitle,
                            onValueChange = { simTitle = it },
                            label = { Text("EXTRA_TITLE") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NothingCrimsonRed,
                                unfocusedBorderColor = NothingBorderHairline,
                                focusedTextColor = NothingPureWhite,
                                unfocusedTextColor = NothingPureWhite
                            )
                        )
                    }

                    OutlinedTextField(
                        value = simContent,
                        onValueChange = { simContent = it },
                        label = { Text("EXTRA_TEXT / NOTIFICATION BODY") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NothingCrimsonRed,
                            unfocusedBorderColor = NothingBorderHairline,
                            focusedTextColor = NothingPureWhite,
                            unfocusedTextColor = NothingPureWhite
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXTRA_PROGRESS (${simProgress.roundToInt()}%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = NothingPureWhite
                        )
                        Switch(
                            checked = includeProgress,
                            onCheckedChange = { includeProgress = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NothingPureWhite,
                                checkedTrackColor = NothingCrimsonRed
                            )
                        )
                    }

                    if (includeProgress) {
                        Slider(
                            value = simProgress,
                            onValueChange = { simProgress = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = NothingCrimsonRed,
                                activeTrackColor = NothingPureWhite
                            )
                        )
                    }

                    Button(
                        onClick = {
                            onSimulateNotification(
                                simPackage,
                                simPackage.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                                simTitle,
                                simContent,
                                if (includeProgress) simProgress.roundToInt() else null
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingCrimsonRed,
                            contentColor = NothingPureWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inject_notification_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "INJECT NOTIFICATION SIGNAL",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickSignalButton(
                            label = "FLIP FACE DOWN",
                            testTag = "sim_face_down_button",
                            onClick = { onSimulatePosture(ConditionType.FACE_DOWN) }
                        )
                        QuickSignalButton(
                            label = "BATTERY 15%",
                            testTag = "sim_low_battery_button",
                            onClick = { onSimulateBattery(15, false, false) }
                        )
                        QuickSignalButton(
                            label = "CHARGING 82%",
                            testTag = "sim_charging_button",
                            onClick = { onSimulateBattery(82, true, true) }
                        )
                        QuickSignalButton(
                            label = "MEDIA PLAYING",
                            testTag = "sim_media_playing_button",
                            onClick = { onSimulateMedia(MediaTriggerState.PLAYING, "Nothing Synth // Track 03") }
                        )
                        QuickSignalButton(
                            label = "ALARM / TIMER",
                            testTag = "sim_alarm_button",
                            onClick = { onSimulatePosture(ConditionType.TIMER_ALARM) }
                        )
                    }
                }
            }
        }

        // 5. Rolling Rule Evaluation Audit Log Header
        item {
            Text(
                text = "RULE EVALUATION LOG (${auditLogs.size}/100)",
                style = MaterialTheme.typography.labelLarge,
                color = NothingTechnicalGray
            )
        }

        if (auditLogs.isEmpty()) {
            item {
                Surface(
                    color = NothingCarbonSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, NothingBorderHairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "No rule evaluation events logged yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = NothingPureWhite
                        )
                        Text(
                            text = "Incoming system notifications and test signals will appear here with rule match and render latency diagnostics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTechnicalGray
                        )
                    }
                }
            }
        } else {
            items(auditLogs, key = { it.id }) { entry ->
                AuditEntryCard(entry = entry, formattedTime = timeFormatter.format(Date(entry.timestamp)))
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickSignalButton(
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, NothingBorderHairline),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier
            .wrapContentHeight()
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NothingPureWhite,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuditEntryCard(
    entry: AuditLogEntry,
    formattedTime: String
) {
    val badgeColor = when (entry.status) {
        AuditStatus.TRIGGERED -> NothingMatrixGreen
        AuditStatus.COOLDOWN_SKIPPED -> NothingSignalAmber
        AuditStatus.PREEMPTED -> NothingCrimsonRed
        AuditStatus.KILL_SWITCH_BLOCKED -> NothingCrimsonRed
        AuditStatus.UNMATCHED -> NothingTechnicalGray
    }

    Surface(
        color = NothingCarbonSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, NothingBorderHairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NothingPillBadge(
                        text = entry.status.badgeText,
                        color = badgeColor
                    )
                    NothingPillBadge(
                        text = entry.eventType.category,
                        color = NothingPureWhite
                    )
                    if (entry.matchedRulePriority != null) {
                        NothingPillBadge(
                            text = "RULE #${entry.matchedRuleId} (P${entry.matchedRulePriority})",
                            color = NothingPureWhite
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$formattedTime • ${entry.renderLatencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingTechnicalGray
                )
            }

            Text(
                text = entry.sourceSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = NothingPureWhite
            )

            Text(
                text = "EXTRAS: ${entry.extrasSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = NothingTechnicalGray
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (entry.matchedRuleName != null) {
                        "${entry.matchedRuleName} → ${entry.actionFiredSummary}"
                    } else {
                        entry.actionFiredSummary
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (entry.status == AuditStatus.TRIGGERED) NothingPureWhite else NothingTechnicalGray,
                    modifier = Modifier.wrapContentHeight()
                )
                Text(
                    text = "${entry.frameName} (PK ${entry.peakBrightness})",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingTechnicalGray,
                    modifier = Modifier.wrapContentHeight()
                )
            }
        }
    }
}
