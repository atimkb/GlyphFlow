package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ActionType
import com.example.domain.model.ConditionType
import com.example.domain.model.GlyphAnimation
import com.example.domain.model.GlyphFrame
import com.example.domain.model.TextMatchOperator
import com.example.domain.model.TriggerRule
import com.example.engine.GlyphPatternGenerator
import com.example.hardware.GlyphHardwareController
import com.example.hardware.HardwareMatrixState
import com.example.ui.components.CompactLiveMatrixCard
import com.example.ui.components.MiniMatrixThumbnail
import com.example.ui.components.NothingPillBadge
import com.example.ui.theme.NothingBorderHairline
import com.example.ui.theme.NothingCarbonSurface
import com.example.ui.theme.NothingCrimsonRed
import com.example.ui.theme.NothingElevatedSurface
import com.example.ui.theme.NothingPureBlack
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.example.engine.ActiveTaskStateManager
import com.example.service.GlyphEngineForegroundService
import com.example.ui.theme.NothingSignalAmber
import com.example.ui.theme.NothingPureWhite
import com.example.ui.theme.NothingTechnicalGray

/**
 * Redesigned Minimalist Nothing OS Dashboard:
 * 1) Compact circular 25x25 live matrix preview at the top.
 * 2) Task Carousel & Transient Interruption Stack.
 * 3) Clean, uncluttered list of Active Automation Cards with progressive disclosure accordions
 *    (or a clean zero-preset canvas when no rules have been created yet).
 * 4) Floating Action Button: `+ Create Rule`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    hardwareState: HardwareMatrixState,
    globalEngineEnabled: Boolean,
    rules: List<TriggerRule>,
    animations: List<GlyphAnimation>,
    isNotificationSnifferActive: Boolean = false,
    capturedSnifferNotification: com.example.domain.model.NotificationPayloadAuditEntry? = null,
    onToggleNotificationSniffer: (Boolean) -> Unit = {},
    onClearCapturedSnifferNotification: () -> Unit = {},
    onGenerateLlmPrompt: (com.example.domain.model.NotificationPayloadAuditEntry) -> Unit = {},
    onStopActiveSession: () -> Unit,
    onVerifyPhysicalToggle: () -> Unit = {},
    onToggleRule: (Long, Boolean) -> Unit,
    onTestRule: (TriggerRule) -> Unit,
    onEditRule: (TriggerRule) -> Unit,
    onEditInBuilder: (TriggerRule) -> Unit = {},
    onDeleteRule: (Long) -> Unit,
    onCreateNewRule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animMap = animations.associateBy { it.id }
    var expandedRuleId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    val ongoingTasks by ActiveTaskStateManager.ongoingTasksFlow.collectAsState()
    val selectedTaskIndex by ActiveTaskStateManager.selectedTaskIndexFlow.collectAsState()
    val activeIntrusion by ActiveTaskStateManager.activeIntrusionFlow.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NothingPureBlack)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("dashboard_rule_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Live Circular Glyph Preview
            item {
                CompactLiveMatrixCard(
                    hardwareState = hardwareState,
                    globalEngineEnabled = globalEngineEnabled,
                    onStopActiveSession = onStopActiveSession,
                    onVerifyPhysicalToggle = onVerifyPhysicalToggle
                )
            }

            // 1a. Notification Sniffer ("Listen & Capture") Mode
            item {
                Surface(
                    color = NothingCarbonSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isNotificationSnifferActive) NothingCrimsonRed else NothingBorderHairline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_sniffer_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = "Notification Sniffer",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NothingPureWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isNotificationSnifferActive) "Sniffer active — waiting for next notification" else "Capture status bar notification into prompt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isNotificationSnifferActive) NothingCrimsonRed else NothingTechnicalGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Switch(
                                checked = isNotificationSnifferActive,
                                onCheckedChange = onToggleNotificationSniffer,
                                modifier = Modifier.testTag("sniffer_toggle_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NothingPureWhite,
                                    checkedTrackColor = NothingCrimsonRed,
                                    uncheckedThumbColor = NothingTechnicalGray,
                                    uncheckedTrackColor = NothingElevatedSurface
                                )
                            )
                        }

                        if (capturedSnifferNotification != null) {
                            val cap = capturedSnifferNotification
                            HorizontalDivider(color = NothingBorderHairline)
                            Surface(
                                color = NothingPureBlack,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, NothingCrimsonRed.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NothingPillBadge(
                                            text = "CAPTURED PAYLOAD",
                                            color = NothingCrimsonRed
                                        )
                                        Text(
                                            text = cap.appLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NothingPureWhite
                                        )
                                    }

                                    Text(
                                        text = "Pkg: ${cap.packageName}",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = NothingTechnicalGray
                                        )
                                    )
                                    Text(
                                        text = "EXTRA_TITLE: \"${cap.rawExtraTitle}\"",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = NothingPureWhite
                                        )
                                    )
                                    Text(
                                        text = "EXTRA_TEXT: \"${cap.rawExtraText}\"",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = NothingPureWhite
                                        )
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { onGenerateLlmPrompt(cap) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = NothingCrimsonRed,
                                                contentColor = NothingPureWhite
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("generate_llm_prompt_button")
                                        ) {
                                            Text(
                                                text = "Prompt AI",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = onClearCapturedSnifferNotification,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, NothingBorderHairline)
                                        ) {
                                            Text(
                                                text = "DISMISS",
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

            // 1b. Transient Intrusion Alert Banner (when active)
            if (activeIntrusion != null) {
                val intrusion = activeIntrusion!!
                item {
                    Surface(
                        color = NothingCarbonSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, NothingCrimsonRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_intrusion_banner")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                NothingPillBadge(
                                    text = "TRANSIENT INTRUSION ACTIVE",
                                    color = NothingCrimsonRed
                                )
                                Text(
                                    text = "DURATION ${intrusion.durationMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NothingTechnicalGray
                                )
                            }
                            Text(
                                text = "HIJACKED DISPLAY: ${intrusion.ruleName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = NothingPureWhite
                            )
                            Text(
                                text = "Source: ${intrusion.appLabel} (${intrusion.sourcePackage}) • Auto-restores background carousel when complete.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTechnicalGray
                            )
                        }
                    }
                }
            }

            // 1c. Dual-Layer Base Ring Buffer (Hardware Carousel)
            if (ongoingTasks.isNotEmpty()) {
                item {
                    Surface(
                        color = NothingCarbonSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, NothingBorderHairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_task_carousel_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "TASK CAROUSEL (${selectedTaskIndex + 1}/${ongoingTasks.size})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NothingPureWhite
                                    )
                                    Text(
                                        text = "Physical Volume Down (Hold 500ms or Double-Click) face-down to swap",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NothingTechnicalGray
                                    )
                                }

                                OutlinedButton(
                                    onClick = { GlyphEngineForegroundService.cycleNextTask(context) },
                                    border = BorderStroke(1.dp, NothingCrimsonRed),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .wrapContentHeight()
                                        .testTag("hardware_cycle_task_button")
                                ) {
                                    Text(
                                        text = "CYCLE TASK (VOL DOWN)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NothingCrimsonRed,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ongoingTasks.forEachIndexed { idx, task ->
                                    val isSelected = idx == selectedTaskIndex
                                    Surface(
                                        color = if (isSelected) NothingCrimsonRed.copy(alpha = 0.18f) else NothingPureBlack,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) NothingCrimsonRed else NothingBorderHairline
                                        ),
                                        modifier = Modifier
                                            .clickable {
                                                if (!isSelected) {
                                                    GlyphEngineForegroundService.cycleNextTask(context)
                                                }
                                            }
                                            .testTag("carousel_task_chip_$idx")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (isSelected) "●" else "○",
                                                color = if (isSelected) NothingCrimsonRed else NothingTechnicalGray,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Text(
                                                text = task.ruleName.ifBlank { task.appLabel },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isSelected) NothingPureWhite else NothingTechnicalGray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE AUTOMATION RULES (${rules.count { it.isEnabled }}/${rules.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = NothingTechnicalGray
                    )
                    Text(
                        text = if (rules.isEmpty()) "CLEAN SLATE" else "TAP CARD FOR DETAILS",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTechnicalGray
                    )
                }
            }

            // 3. Clean Empty State when 0 rules exist
            if (rules.isEmpty()) {
                item {
                    Surface(
                        color = NothingCarbonSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, NothingBorderHairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .testTag("dashboard_empty_rules_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NothingPillBadge(
                                text = "0 ACTIVE RULES // CLEAN CANVAS",
                                color = NothingCrimsonRed
                            )
                            Text(
                                text = "No Preloaded Rules or Presets",
                                style = MaterialTheme.typography.titleMedium,
                                color = NothingPureWhite
                            )
                            Text(
                                text = "Build every Essential notification rule, sender/body filter, Quick Settings broadcast trigger, and 25×25 circular animation from scratch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTechnicalGray
                            )
                            Button(
                                onClick = onCreateNewRule,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingCrimsonRed,
                                    contentColor = NothingPureWhite
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .wrapContentHeight()
                                    .testTag("empty_state_create_rule_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CREATE FIRST RULE",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // 4. Accordion Automation Rule Cards
            items(rules, key = { it.id }) { rule ->
                val linkedAnim = animMap[rule.targetAnimationId]
                val thumbPixels = remember(rule.id, rule.updatedAt, rule.thumbnailBytes) {
                    if (rule.thumbnailBytes.isNotEmpty()) {
                        rule.thumbnailBytes
                    } else {
                        when (rule.actionType) {
                            ActionType.MATRIX_METER -> GlyphHardwareController.buildMeterFrame(
                                percentage = 75,
                                style = rule.meterStyle,
                                peakBrightness = rule.meterPeakBrightness
                            )
                            ActionType.AUDIO_WAVEFORM -> GlyphPatternGenerator.buildAudioWaveformShaderFrame(
                                tick = 2,
                                peakBrightness = rule.meterPeakBrightness
                            )
                            else -> linkedAnim?.frames?.firstOrNull()?.pixels
                                ?: GlyphFrame().pixels
                        }
                    }
                }
                val thumbFrames = remember(rule.id, rule.updatedAt, rule.thumbnailBytes, linkedAnim?.id) {
                    if (rule.thumbnailBytes.isNotEmpty()) {
                        listOf(rule.thumbnailBytes)
                    } else {
                        linkedAnim?.frames?.map { it.pixels } ?: emptyList()
                    }
                }

                AccordionRuleCard(
                    rule = rule,
                    thumbnailPixels = thumbPixels,
                    thumbnailFrames = thumbFrames,
                    isExpanded = expandedRuleId == rule.id,
                    onToggleExpand = {
                        expandedRuleId = if (expandedRuleId == rule.id) null else rule.id
                    },
                    onToggleEnabled = { enabled -> onToggleRule(rule.id, enabled) },
                    onTestFire = { onTestRule(rule) },
                    onEdit = { onEditRule(rule) },
                    onEditInBuilder = { onEditInBuilder(rule) },
                    onDelete = { onDeleteRule(rule.id) }
                )
            }
        }

        // Floating Action Button: + Create Rule
        ExtendedFloatingActionButton(
            onClick = onCreateNewRule,
            containerColor = NothingCrimsonRed,
            contentColor = NothingPureWhite,
            shape = RoundedCornerShape(50),
            icon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Rule"
                )
            },
            text = {
                Text(
                    text = "+ Create Rule",
                    style = MaterialTheme.typography.labelLarge
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .testTag("create_new_rule_button")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccordionRuleCard(
    rule: TriggerRule,
    thumbnailPixels: ByteArray,
    thumbnailFrames: List<ByteArray> = emptyList(),
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTestFire: () -> Unit,
    onEdit: () -> Unit,
    onEditInBuilder: () -> Unit = {},
    onDelete: () -> Unit
) {
    Surface(
        color = NothingCarbonSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpanded) NothingCrimsonRed.copy(alpha = 0.65f) else NothingBorderHairline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onToggleExpand() }
            .testTag("rule_card_${rule.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Collapsed Summary Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    MiniMatrixThumbnail(
                        pixels = thumbnailPixels,
                        frames = thumbnailFrames,
                        size = 48.dp,
                        isSelected = isExpanded
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (rule.isEnabled) NothingPureWhite else NothingTechnicalGray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = describeCleanRuleSummary(rule),
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTechnicalGray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse Details" else "Expand Details",
                        tint = NothingTechnicalGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.testTag("rule_switch_${rule.id}"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NothingPureWhite,
                            checkedTrackColor = NothingCrimsonRed,
                            uncheckedThumbColor = NothingTechnicalGray,
                            uncheckedTrackColor = NothingElevatedSurface
                        )
                    )
                }
            }

            // Accordion Progressive Disclosure Content
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = NothingBorderHairline)

                    // Trigger, Essential Priority & Reactivity Metadata Pills
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (rule.essentialPriorityOverride) {
                            NothingPillBadge(
                                text = "ESSENTIAL OVERRIDE (P10)",
                                color = NothingCrimsonRed
                            )
                        } else {
                            NothingPillBadge(
                                text = "PRIORITY P${rule.effectivePriority}",
                                color = if (rule.effectivePriority >= 8) NothingCrimsonRed else NothingPureWhite
                            )
                        }

                        NothingPillBadge(
                            text = if (rule.holdUntilConditionClears || rule.essentialPriorityOverride) {
                                "UNTIL DISMISSED / CLEARED"
                            } else {
                                "${rule.overrideDurationMs / 1000.0}S FIXED"
                            },
                            color = NothingPureWhite
                        )

                        if (rule.actionType == ActionType.AUDIO_WAVEFORM ||
                            rule.conditionType == ConditionType.MEDIA_PLAYBACK
                        ) {
                            NothingPillBadge(
                                text = "AUDIO REACTIVE 30 FPS",
                                color = NothingCrimsonRed
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        com.example.ui.components.CircularGlyphMatrixPreview(
                            pixels = thumbnailPixels,
                            interactive = false,
                            showCircularBezelOverlay = true,
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            text = buildDetailedRuleDescription(rule),
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTechnicalGray,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Quick Action Bar inside Accordion
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onTestFire,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingCrimsonRed,
                                contentColor = NothingPureWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .wrapContentHeight()
                                .testTag("test_rule_button_${rule.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Test Rule",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TEST STREAM",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        OutlinedButton(
                            onClick = onEdit,
                            border = BorderStroke(1.dp, NothingBorderHairline),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .wrapContentHeight()
                                .testTag("edit_rule_button_${rule.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Rule",
                                tint = NothingPureWhite,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "EDIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = NothingPureWhite,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        OutlinedButton(
                            onClick = onEditInBuilder,
                            border = BorderStroke(1.dp, NothingCrimsonRed.copy(alpha = 0.8f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = NothingCrimsonRed.copy(alpha = 0.12f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .wrapContentHeight()
                                .testTag("edit_in_builder_button_${rule.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = "Edit in Visual Builder",
                                tint = NothingCrimsonRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "BUILDER",
                                style = MaterialTheme.typography.labelSmall,
                                color = NothingPureWhite,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        OutlinedButton(
                            onClick = onDelete,
                            border = BorderStroke(1.dp, NothingBorderHairline),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .wrapContentHeight()
                                .testTag("delete_rule_button_${rule.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Rule",
                                tint = NothingTechnicalGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun describeCleanRuleSummary(rule: TriggerRule): String {
    val conditionPart = when (rule.conditionType) {
        ConditionType.NOTIFICATION -> {
            val baseApp = if (rule.targetPackageName == "*") "Any App" else rule.targetAppLabel
            val filters = buildList {
                if (rule.senderFilterOperator != TextMatchOperator.ANY && rule.senderFilterPattern.isNotBlank()) {
                    add("Sender: ${rule.senderFilterPattern}")
                }
                if (rule.bodyFilterOperator != TextMatchOperator.ANY && rule.bodyFilterPattern.isNotBlank()) {
                    add("Body: ${rule.bodyFilterPattern}")
                }
            }
            if (filters.isEmpty()) baseApp else "$baseApp (${filters.joinToString(", ")})"
        }
        ConditionType.BATTERY_LEVEL ->
            "Battery ${rule.numericComparison.symbol} ${rule.numericThreshold}%"
        ConditionType.CHARGING_STATE ->
            rule.chargingRequirement.label
        ConditionType.LOW_POWER_MODE ->
            "Battery Saver"
        ConditionType.MEDIA_PLAYBACK ->
            "Audio Playing"
        ConditionType.SCREEN_STATE ->
            "Always-On (Screen Off)"
        ConditionType.FACE_DOWN ->
            "Always-On (Flip to Glyph)"
        ConditionType.TIMER_ALARM ->
            "Timer / Stopwatch"
        ConditionType.BLUETOOTH_STATE,
        ConditionType.WIFI_STATE,
        ConditionType.RINGER_MODE,
        ConditionType.HOTSPOT_STATE,
        ConditionType.TORCH_STATE,
        ConditionType.AIRPLANE_MODE,
        ConditionType.SYSTEM_STATE,
        ConditionType.KEY_PRESS ->
            "${rule.conditionType.label} (${rule.systemToggleFilter.label})"
    }

    val actionPart = when (rule.actionType) {
        ActionType.SHOW_SINGLE_FRAME -> rule.targetAnimationName
        ActionType.PLAY_ANIMATION -> rule.targetAnimationName
        ActionType.AUDIO_WAVEFORM -> "Audio Waveform Shader"
        ActionType.MATRIX_METER -> rule.meterStyle.label
        ActionType.GLYPH_SCRIPT_COMPOSITOR -> "GlyphScript v2 Compositor"
    }

    return "$conditionPart → $actionPart"
}

private fun buildDetailedRuleDescription(rule: TriggerRule): String {
    return when (rule.conditionType) {
        ConditionType.NOTIFICATION -> {
            val appText = if (rule.targetPackageName == "*") "any installed app" else "${rule.targetAppLabel} (${rule.targetPackageName})"
            val senderClause = if (rule.senderFilterOperator != TextMatchOperator.ANY && rule.senderFilterPattern.isNotBlank()) {
                " • Sender/Title ${rule.senderFilterOperator.label}: \"${rule.senderFilterPattern}\""
            } else ""
            val bodyClause = if (rule.bodyFilterOperator != TextMatchOperator.ANY && rule.bodyFilterPattern.isNotBlank()) {
                " • Body ${rule.bodyFilterOperator.label}: \"${rule.bodyFilterPattern}\""
            } else ""
            val essentialClause = if (rule.essentialPriorityOverride) {
                " • Essential Priority Override (stays active until notification dismissed)"
            } else ""
            "Triggers when $appText posts a notification$senderClause$bodyClause$essentialClause."
        }
        ConditionType.MEDIA_PLAYBACK ->
            "Streams continuously at 30 FPS while media/audio playback is active and automatically clears to 0 when paused."
        ConditionType.BATTERY_LEVEL, ConditionType.CHARGING_STATE ->
            "Monitors battery & power telemetry (${rule.chargingRequirement.label}, threshold ${rule.numericComparison.symbol} ${rule.numericThreshold}%)."
        ConditionType.TIMER_ALARM ->
            "Activates when a system timer, stopwatch, or alarm broadcast fires."
        ConditionType.FACE_DOWN, ConditionType.SCREEN_STATE, ConditionType.LOW_POWER_MODE ->
            "Activates on Nothing OS posture/display change (${rule.conditionType.label})."
        ConditionType.BLUETOOTH_STATE,
        ConditionType.WIFI_STATE,
        ConditionType.RINGER_MODE,
        ConditionType.HOTSPOT_STATE,
        ConditionType.TORCH_STATE,
        ConditionType.AIRPLANE_MODE,
        ConditionType.SYSTEM_STATE,
        ConditionType.KEY_PRESS ->
            "Listens to system broadcast for ${rule.conditionType.label} when state matches '${rule.systemToggleFilter.label}'."
    }
}
