package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.domain.model.ActionType
import com.example.domain.model.ChargingRequirement
import com.example.domain.model.ConditionType
import com.example.domain.model.GlyphAnimation
import com.example.domain.model.InstalledAppOption
import com.example.domain.model.MeterStyle
import com.example.domain.model.SystemToggleFilter
import com.example.domain.model.TextMatchOperator
import com.example.domain.model.TriggerRule
import com.example.ui.components.MiniMatrixThumbnail
import com.example.ui.components.NothingCompactPill
import com.example.ui.components.NothingPillBadge
import com.example.ui.theme.NothingBorderHairline
import com.example.ui.theme.NothingCarbonSurface
import com.example.ui.theme.NothingCrimsonRed
import com.example.ui.theme.NothingElevatedSurface
import com.example.ui.theme.NothingPureBlack
import com.example.ui.theme.NothingPureWhite
import com.example.ui.theme.NothingTechnicalGray
import kotlin.math.roundToInt

private val QUICK_SETTINGS_CONDITIONS = listOf(
    ConditionType.BLUETOOTH_STATE,
    ConditionType.WIFI_STATE,
    ConditionType.RINGER_MODE,
    ConditionType.HOTSPOT_STATE,
    ConditionType.TORCH_STATE,
    ConditionType.AIRPLANE_MODE
)

private data class StreamlinedTriggerOption(
    val title: String,
    val subtitle: String,
    val conditionType: ConditionType,
    val isQuickSettingsGroup: Boolean = false
)

private val STREAMLINED_TRIGGERS = listOf(
    StreamlinedTriggerOption(
        title = "Essential Notification Filter",
        subtitle = "Target app + Sender/Title match + Message body keywords + Priority override",
        conditionType = ConditionType.NOTIFICATION
    ),
    StreamlinedTriggerOption(
        title = "System State & Hardware Triggers",
        subtitle = "Bluetooth, Battery, Wi-Fi, Ringer, Screen, or Active App states",
        conditionType = ConditionType.SYSTEM_STATE
    ),
    StreamlinedTriggerOption(
        title = "Key Press & Temporal Sequence",
        subtitle = "Hardware key sequences (e.g., Volume Up -> Volume Down within timeout)",
        conditionType = ConditionType.KEY_PRESS
    ),
    StreamlinedTriggerOption(
        title = "Quick Settings & System Toggles",
        subtitle = "Bluetooth, Wi-Fi, Ringer (Silent/Vibrate/Normal), Hotspot, Torch & Airplane mode",
        conditionType = ConditionType.BLUETOOTH_STATE,
        isQuickSettingsGroup = true
    ),
    StreamlinedTriggerOption(
        title = "Audio / Media Playing",
        subtitle = "Continuous 30 FPS stream while music or media is actively playing",
        conditionType = ConditionType.MEDIA_PLAYBACK
    ),
    StreamlinedTriggerOption(
        title = "Battery / Charging",
        subtitle = "When charging or below battery % threshold",
        conditionType = ConditionType.BATTERY_LEVEL
    ),
    StreamlinedTriggerOption(
        title = "Timer / Stopwatch",
        subtitle = "When system timer or alarm broadcast is active",
        conditionType = ConditionType.TIMER_ALARM
    ),
    StreamlinedTriggerOption(
        title = "Always-on Display Mode",
        subtitle = "Flip to Glyph (Face Down) or Screen Off",
        conditionType = ConditionType.FACE_DOWN
    )
)

/**
 * Granular 3-Step Accordion Rule Creator:
 * - Step 1: When this happens (Essential Notification Deep Filtering + Quick Settings Toggles + System Signals)
 * - Step 2: What to show (Saved Presets, Draw on the Fly, or Dynamic Reactive Modes)
 * - Step 3: Duration & Behavior ('Until condition stops' vs 'Fixed Duration' + Essential Priority Override)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleBuilderScreen(
    draftRule: TriggerRule,
    animations: List<GlyphAnimation>,
    installedApps: List<InstalledAppOption>,
    onUpdateDraft: ((TriggerRule) -> TriggerRule) -> Unit,
    onSaveRule: () -> Unit,
    onTestDraftRule: (TriggerRule) -> Unit,
    onDrawOnTheFly: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var openAccordionStep by remember { mutableIntStateOf(1) }
    var showAppPickerDialog by remember { mutableStateOf(false) }

    if (showAppPickerDialog) {
        InstalledAppPickerDialog(
            apps = installedApps,
            selectedPackage = draftRule.targetPackageName,
            onSelectApp = { app ->
                onUpdateDraft {
                    it.copy(
                        targetPackageName = app.packageName,
                        targetAppLabel = app.appName,
                        name = if (it.name == "Custom Glyph Rule" || it.name.isBlank()) {
                            "${app.appName} Glyph Alert"
                        } else {
                            it.name
                        }
                    )
                }
                showAppPickerDialog = false
            },
            onDismiss = { showAppPickerDialog = false }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NothingPureBlack)
            .testTag("rule_builder_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header & Rule Name
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("close_rule_creator_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Rule Creator",
                                tint = NothingPureWhite
                            )
                        }
                        Text(
                            text = if (draftRule.id == 0L) "CREATE RULE" else "EDIT RULE",
                            style = MaterialTheme.typography.titleLarge,
                            color = NothingPureWhite
                        )
                    }

                    OutlinedButton(
                        onClick = { onTestDraftRule(draftRule) },
                        border = BorderStroke(1.dp, NothingCrimsonRed),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .wrapContentHeight()
                            .testTag("builder_test_rule_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Test Rule",
                            tint = NothingCrimsonRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "TEST LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingPureWhite
                        )
                    }
                }

                OutlinedTextField(
                    value = draftRule.name,
                    onValueChange = { text -> onUpdateDraft { it.copy(name = text) } },
                    label = { Text("RULE NAME") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("builder_rule_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NothingCrimsonRed,
                        unfocusedBorderColor = NothingBorderHairline,
                        focusedTextColor = NothingPureWhite,
                        unfocusedTextColor = NothingPureWhite
                    )
                )
            }
        }

        // STEP 1 ACCORDION: WHEN THIS HAPPENS (TRIGGER)
        item {
            AccordionStepContainer(
                stepNumber = "STEP 01",
                title = "When this happens (Trigger)",
                summary = summarizeStep1(draftRule),
                isExpanded = openAccordionStep == 1,
                onToggle = { openAccordionStep = if (openAccordionStep == 1) 0 else 1 },
                testTag = "accordion_step_1"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    STREAMLINED_TRIGGERS.forEach { option ->
                        val isSelected = if (option.isQuickSettingsGroup) {
                            draftRule.conditionType in QUICK_SETTINGS_CONDITIONS
                        } else {
                            draftRule.conditionType == option.conditionType ||
                                (option.conditionType == ConditionType.BATTERY_LEVEL && draftRule.conditionType == ConditionType.CHARGING_STATE) ||
                                (option.conditionType == ConditionType.FACE_DOWN && draftRule.conditionType == ConditionType.SCREEN_STATE)
                        }

                        Surface(
                            color = if (isSelected) NothingCrimsonRed.copy(alpha = 0.16f) else NothingElevatedSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) NothingCrimsonRed else NothingBorderHairline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clickable {
                                    onUpdateDraft { current ->
                                        val nextAction = if (option.conditionType == ConditionType.MEDIA_PLAYBACK) {
                                            ActionType.AUDIO_WAVEFORM
                                        } else {
                                            current.actionType
                                        }
                                        val nextCondition = if (option.isQuickSettingsGroup && current.conditionType !in QUICK_SETTINGS_CONDITIONS) {
                                            ConditionType.BLUETOOTH_STATE
                                        } else if (!option.isQuickSettingsGroup) {
                                            option.conditionType
                                        } else {
                                            current.conditionType
                                        }
                                        current.copy(
                                            conditionType = nextCondition,
                                            actionType = nextAction,
                                            holdUntilConditionClears = !option.isQuickSettingsGroup
                                        )
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = NothingPureWhite
                                    )
                                    Text(
                                        text = option.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NothingTechnicalGray
                                    )
                                }
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    NothingPillBadge(text = "SELECTED", color = NothingCrimsonRed)
                                }
                            }
                        }
                    }

                    // 1A. ESSENTIAL NOTIFICATION DEEP FILTERING
                    if (draftRule.conditionType == ConditionType.NOTIFICATION) {
                        HorizontalDivider(color = NothingBorderHairline)
                        Text(
                            text = "1. TARGET APPLICATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTechnicalGray
                        )

                        Surface(
                            color = NothingElevatedSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NothingBorderHairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clickable { showAppPickerDialog = true }
                        ) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    AppIconBadge(
                                        packageName = draftRule.targetPackageName,
                                        appName = draftRule.targetAppLabel,
                                        sizeDp = 38
                                    )
                                    Column {
                                        Text(
                                            text = draftRule.targetAppLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = NothingPureWhite
                                        )
                                        Text(
                                            text = if (draftRule.targetPackageName == "*")
                                                "Matches notifications from any app (*)"
                                            else
                                                draftRule.targetPackageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NothingTechnicalGray
                                        )
                                    }
                                }

                                Button(
                                    onClick = { showAppPickerDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NothingCrimsonRed,
                                        contentColor = NothingPureWhite
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .wrapContentHeight()
                                        .testTag("pick_installed_app_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PICK APP",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = NothingBorderHairline)

                        // Sender / Title Granular Filter
                        Text(
                            text = "2. SENDER / CONVERSATION TITLE FILTER (E.G. 'MOM', 'BOSS', 'VIP GROUP')",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTechnicalGray
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextMatchOperator.entries.forEach { op ->
                                NothingCompactPill(
                                    label = op.label,
                                    selected = draftRule.senderFilterOperator == op,
                                    onClick = {
                                        onUpdateDraft { it.copy(senderFilterOperator = op) }
                                    }
                                )
                            }
                        }

                        if (draftRule.senderFilterOperator != TextMatchOperator.ANY) {
                            OutlinedTextField(
                                value = draftRule.senderFilterPattern,
                                onValueChange = { text ->
                                    onUpdateDraft { it.copy(senderFilterPattern = text) }
                                },
                                label = { Text("SENDER OR GROUP NAME (${draftRule.senderFilterOperator.label.uppercase()})") },
                                placeholder = { Text("e.g. Mom, Boss, Delivery, VIP Group") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sender_filter_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NothingCrimsonRed,
                                    unfocusedBorderColor = NothingBorderHairline,
                                    focusedTextColor = NothingPureWhite,
                                    unfocusedTextColor = NothingPureWhite
                                )
                            )
                        }

                        HorizontalDivider(color = NothingBorderHairline)

                        // Message Body Granular Filter
                        Text(
                            text = "3. MESSAGE BODY KEYWORD / REGEX FILTER (E.G. 'URGENT', 'OTP', 'CODE')",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTechnicalGray
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                TextMatchOperator.ANY,
                                TextMatchOperator.CONTAINS,
                                TextMatchOperator.EQUALS,
                                TextMatchOperator.REGEX
                            ).forEach { op ->
                                NothingCompactPill(
                                    label = op.label,
                                    selected = draftRule.bodyFilterOperator == op,
                                    onClick = {
                                        onUpdateDraft { it.copy(bodyFilterOperator = op) }
                                    }
                                )
                            }
                        }

                        if (draftRule.bodyFilterOperator != TextMatchOperator.ANY) {
                            OutlinedTextField(
                                value = draftRule.bodyFilterPattern,
                                onValueChange = { text ->
                                    onUpdateDraft { it.copy(bodyFilterPattern = text) }
                                },
                                label = { Text("MESSAGE BODY KEYWORD (${draftRule.bodyFilterOperator.label.uppercase()})") },
                                placeholder = { Text("e.g. urgent, OTP, verification code") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("body_filter_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NothingCrimsonRed,
                                    unfocusedBorderColor = NothingBorderHairline,
                                    focusedTextColor = NothingPureWhite,
                                    unfocusedTextColor = NothingPureWhite
                                )
                            )
                        }

                        // Essential Priority Override Switch
                        Surface(
                            color = if (draftRule.essentialPriorityOverride)
                                NothingCrimsonRed.copy(alpha = 0.16f)
                            else
                                NothingElevatedSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (draftRule.essentialPriorityOverride) NothingCrimsonRed else NothingBorderHairline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clickable {
                                    onUpdateDraft {
                                        val next = !it.essentialPriorityOverride
                                        it.copy(
                                            essentialPriorityOverride = next,
                                            priority = if (next) 10 else it.priority,
                                            holdUntilConditionClears = if (next) true else it.holdUntilConditionClears
                                        )
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Essential Priority Override (P10)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = NothingPureWhite
                                    )
                                    Text(
                                        text = "Elevates this rule to highest priority and locks the Glyph onto the rear matrix until the notification is dismissed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NothingTechnicalGray
                                    )
                                }
                                Switch(
                                    checked = draftRule.essentialPriorityOverride,
                                    onCheckedChange = { checked ->
                                        onUpdateDraft {
                                            it.copy(
                                                essentialPriorityOverride = checked,
                                                priority = if (checked) 10 else it.priority,
                                                holdUntilConditionClears = if (checked) true else it.holdUntilConditionClears
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("essential_priority_override_switch"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NothingPureWhite,
                                        checkedTrackColor = NothingCrimsonRed
                                    )
                                )
                            }
                        }
                    }

                    // 1B. QUICK SETTINGS & SYSTEM STATE BROADCAST TRIGGERS
                    if (draftRule.conditionType in QUICK_SETTINGS_CONDITIONS) {
                        HorizontalDivider(color = NothingBorderHairline)
                        Text(
                            text = "SELECT QUICK SETTINGS / SYSTEM TOGGLE",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTechnicalGray
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QUICK_SETTINGS_CONDITIONS.forEach { qsType ->
                                NothingCompactPill(
                                    label = qsType.label,
                                    selected = draftRule.conditionType == qsType,
                                    onClick = {
                                        onUpdateDraft { current ->
                                            val defaultFilter = if (qsType == ConditionType.RINGER_MODE) {
                                                SystemToggleFilter.RINGER_VIBRATE
                                            } else {
                                                SystemToggleFilter.ENABLED_ON
                                            }
                                            current.copy(
                                                conditionType = qsType,
                                                systemToggleFilter = defaultFilter,
                                                name = "${qsType.label} (${defaultFilter.label})"
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        Text(
                            text = "TRIGGER WHEN STATE IS:",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTechnicalGray
                        )

                        val availableFilters = if (draftRule.conditionType == ConditionType.RINGER_MODE) {
                            listOf(
                                SystemToggleFilter.RINGER_SILENT,
                                SystemToggleFilter.RINGER_VIBRATE,
                                SystemToggleFilter.RINGER_NORMAL,
                                SystemToggleFilter.ANY_CHANGE
                            )
                        } else {
                            listOf(
                                SystemToggleFilter.ENABLED_ON,
                                SystemToggleFilter.DISABLED_OFF,
                                SystemToggleFilter.ANY_CHANGE
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableFilters.forEach { filterOption ->
                                NothingCompactPill(
                                    label = filterOption.label,
                                    selected = draftRule.systemToggleFilter == filterOption,
                                    onClick = {
                                        onUpdateDraft {
                                            it.copy(systemToggleFilter = filterOption)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // 1C. BATTERY / CHARGING CONFIG
                    if (draftRule.conditionType == ConditionType.BATTERY_LEVEL ||
                        draftRule.conditionType == ConditionType.CHARGING_STATE
                    ) {
                        HorizontalDivider(color = NothingBorderHairline)
                        Text(
                            text = "BATTERY THRESHOLD: ${draftRule.numericThreshold}% (${draftRule.chargingRequirement.label})",
                            style = MaterialTheme.typography.labelMedium,
                            color = NothingPureWhite
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChargingRequirement.entries.forEach { req ->
                                NothingCompactPill(
                                    label = req.label,
                                    selected = draftRule.chargingRequirement == req,
                                    onClick = { onUpdateDraft { it.copy(chargingRequirement = req) } }
                                )
                            }
                        }
                        Slider(
                            value = draftRule.numericThreshold.toFloat(),
                            onValueChange = { v -> onUpdateDraft { it.copy(numericThreshold = v.roundToInt()) } },
                            valueRange = 5f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = NothingCrimsonRed,
                                activeTrackColor = NothingPureWhite
                            )
                        )
                    }

                    // 1D. SYSTEM STATE CASCADING HARDWARE & STATE SELECTORS + MULTI-TRIGGER (OR)
                    if (draftRule.conditionType == ConditionType.SYSTEM_STATE || draftRule.conditionType == ConditionType.NOTIFICATION) {
                        HorizontalDivider(color = NothingBorderHairline)
                        var selectedHardware by remember { mutableStateOf("bluetooth") }
                        var selectedState by remember { mutableStateOf("CHANGED") }
                        var orTriggers by remember { mutableStateOf(listOf<String>()) }

                        Text(
                            text = "SYSTEM OS TRIGGER PROPERTY",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTechnicalGray
                        )
                        val hardwareList = listOf(
                            "bluetooth" to "system.bluetooth",
                            "power" to "system.power",
                            "battery_level" to "system.battery_level",
                            "wifi" to "system.wifi",
                            "ringer" to "system.ringer",
                            "app.foreground" to "app.foreground"
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            hardwareList.forEach { (hwKey, hwLabel) ->
                                NothingCompactPill(
                                    label = hwLabel,
                                    selected = selectedHardware == hwKey,
                                    onClick = {
                                        selectedHardware = hwKey
                                        selectedState = when (hwKey) {
                                            "ringer" -> "silent"
                                            "battery_level" -> "< 15"
                                            "power" -> "charging"
                                            "app.foreground" -> "com.whatsapp"
                                            else -> "CHANGED"
                                        }
                                        onUpdateDraft { r ->
                                            val baseTrig = if (r.conditionType == ConditionType.NOTIFICATION) {
                                                "notification FROM [\"${r.targetPackageName.ifBlank { "*" }}\"]"
                                            } else {
                                                if (hwKey == "app.foreground") "app.foreground == \"$selectedState\""
                                                else if (selectedState == "CHANGED") "system.$hwKey CHANGED"
                                                else "system.$hwKey == \"$selectedState\""
                                            }
                                            val fullWhen = (listOf(baseTrig) + orTriggers).joinToString(" OR ")
                                            val dslCode = "RULE \"${r.name}\"\nWHEN $fullWhen\nDISPLAY {\n  LAYER indicator {\n    icon(name: \"bell\", pulse: true)\n  }\n}\nBEHAVIOR {\n  priority: ${r.priority}\n  until: duration(5s)\n}"
                                            r.copy(glyphScriptSource = dslCode)
                                        }
                                    }
                                )
                            }
                        }

                        Text(
                            text = "VALUE / CONDITION",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTechnicalGray
                        )
                        val stateOptions = when (selectedHardware) {
                            "bluetooth" -> listOf("connected" to "connected", "disconnected" to "disconnected", "bt_on" to "bt_on", "bt_off" to "bt_off", "CHANGED" to "CHANGED")
                            "wifi" -> listOf("connected" to "connected", "disconnected" to "disconnected", "CHANGED" to "CHANGED")
                            "power" -> listOf("charging" to "charging", "discharging" to "discharging", "full" to "full", "CHANGED" to "CHANGED")
                            "battery_level" -> listOf("< 15" to "< 15", "< 20" to "< 20", "< 50" to "< 50", ">= 80" to ">= 80", "CHANGED" to "CHANGED")
                            "ringer" -> listOf("silent" to "silent", "vibrate" to "vibrate", "normal" to "normal", "CHANGED" to "CHANGED")
                            "app.foreground" -> listOf("com.whatsapp" to "WhatsApp", "com.spotify.music" to "Spotify", "in.swiggy.android" to "Swiggy", "com.google.android.apps.maps" to "Maps")
                            else -> listOf("connected" to "connected", "CHANGED" to "CHANGED")
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            stateOptions.forEach { (stateKey, stateLabel) ->
                                NothingCompactPill(
                                    label = stateLabel,
                                    selected = selectedState == stateKey,
                                    onClick = {
                                        selectedState = stateKey
                                        onUpdateDraft { r ->
                                            val baseTrig = if (r.conditionType == ConditionType.NOTIFICATION) {
                                                "notification FROM [\"${r.targetPackageName.ifBlank { "*" }}\"]"
                                            } else {
                                                if (selectedHardware == "app.foreground") "app.foreground == \"$stateKey\""
                                                else if (stateKey == "CHANGED") "system.$selectedHardware CHANGED"
                                                else if (stateKey.startsWith("<") || stateKey.startsWith(">")) "system.$selectedHardware $stateKey"
                                                else "system.$selectedHardware == \"$stateKey\""
                                            }
                                            val fullWhen = (listOf(baseTrig) + orTriggers).joinToString(" OR ")
                                            val dslCode = "RULE \"${r.name}\"\nWHEN $fullWhen\nDISPLAY {\n  LAYER indicator {\n    icon(name: \"bell\", pulse: true)\n  }\n}\nBEHAVIOR {\n  priority: ${r.priority}\n  until: duration(5s)\n}"
                                            r.copy(glyphScriptSource = dslCode)
                                        }
                                    }
                                )
                            }
                        }

                        // MULTI-TRIGGER (OR) BUILDER BUTTON
                        HorizontalDivider(color = NothingBorderHairline)
                        if (orTriggers.isNotEmpty()) {
                            Text(
                                text = "ACTIVE MULTI-TRIGGERS (OR):",
                                style = MaterialTheme.typography.labelSmall,
                                color = NothingTechnicalGray
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                orTriggers.forEachIndexed { idx, trigStr ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NothingCarbonSurface)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "OR $trigStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NothingPureWhite
                                        )
                                        IconButton(
                                            onClick = {
                                                orTriggers = orTriggers.toMutableList().apply { removeAt(idx) }
                                                onUpdateDraft { r ->
                                                    val baseTrig = if (r.conditionType == ConditionType.NOTIFICATION) {
                                                        "notification FROM [\"${r.targetPackageName.ifBlank { "*" }}\"]"
                                                    } else {
                                                        if (selectedHardware == "app.foreground") "app.foreground == \"$selectedState\""
                                                        else if (selectedState == "CHANGED") "system.$selectedHardware CHANGED"
                                                        else "system.$selectedHardware == \"$selectedState\""
                                                    }
                                                    val fullWhen = (listOf(baseTrig) + orTriggers).joinToString(" OR ")
                                                    val dslCode = "RULE \"${r.name}\"\nWHEN $fullWhen\nDISPLAY {\n  LAYER indicator {\n    icon(name: \"bell\", pulse: true)\n  }\n}\nBEHAVIOR {\n  priority: ${r.priority}\n  until: duration(5s)\n}"
                                                    r.copy(glyphScriptSource = dslCode)
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove Trigger",
                                                tint = NothingCrimsonRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val newOrTrig = if (selectedHardware == "app.foreground") {
                                    "app.foreground == \"$selectedState\""
                                } else if (selectedState == "CHANGED") {
                                    "system.$selectedHardware CHANGED"
                                } else if (selectedState.startsWith("<") || selectedState.startsWith(">")) {
                                    "system.$selectedHardware $selectedState"
                                } else {
                                    "system.$selectedHardware == \"$selectedState\""
                                }
                                if (!orTriggers.contains(newOrTrig)) {
                                    orTriggers = orTriggers + newOrTrig
                                    onUpdateDraft { r ->
                                        val baseTrig = if (r.conditionType == ConditionType.NOTIFICATION) {
                                            "notification FROM [\"${r.targetPackageName.ifBlank { "*" }}\"]"
                                        } else {
                                            "system.bluetooth CHANGED"
                                        }
                                        val fullWhen = (listOf(baseTrig) + orTriggers).joinToString(" OR ")
                                        val dslCode = "RULE \"${r.name}\"\nWHEN $fullWhen\nDISPLAY {\n  LAYER indicator {\n    icon(name: \"bell\", pulse: true)\n  }\n}\nBEHAVIOR {\n  priority: ${r.priority}\n  until: duration(5s)\n}"
                                        r.copy(glyphScriptSource = dslCode)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, NothingCrimsonRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_trigger_or_button")
                        ) {
                            Text(
                                text = "+ ADD TRIGGER (OR)",
                                style = MaterialTheme.typography.labelMedium,
                                color = NothingCrimsonRed
                            )
                        }
                    }

                    // 1E. KEY PRESS & TEMPORAL SEQUENCE CONTROLS
                    if (draftRule.conditionType == ConditionType.KEY_PRESS) {
                        HorizontalDivider(color = NothingBorderHairline)
                        var sequencePreset by remember { mutableStateOf("vol_up_vol_down") }

                        Text(
                            text = "TEMPORAL KEY SEQUENCE PRESET",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTechnicalGray
                        )
                        val seqOptions = listOf(
                            "vol_up_vol_down" to "Volume Up → Volume Down",
                            "vol_up_vol_up" to "Volume Up → Volume Up",
                            "vol_down_vol_down" to "Volume Down → Volume Down"
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            seqOptions.forEach { (presetKey, presetLabel) ->
                                NothingCompactPill(
                                    label = presetLabel,
                                    selected = sequencePreset == presetKey,
                                    onClick = {
                                        sequencePreset = presetKey
                                        val events = when (presetKey) {
                                            "vol_up_vol_down" -> listOf("key.volume_up", "key.volume_down")
                                            "vol_up_vol_up" -> listOf("key.volume_up", "key.volume_up")
                                            else -> listOf("key.volume_down", "key.volume_down")
                                        }
                                        onUpdateDraft { r ->
                                            val dslCode = "RULE \"${r.name}\"\nSEQUENCE (timeout: ${r.sequenceTimeoutMs}ms) {\n${events.joinToString("\n") { "  $it" }}\n}\nDISPLAY {\n  LAYER indicator {\n    icon(name: \"bell\", pulse: true)\n  }\n}\nBEHAVIOR {\n  priority: ${r.priority}\n  until: duration(5s)\n}"
                                            r.copy(
                                                sequenceEvents = events,
                                                glyphScriptSource = dslCode
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        Text(
                            text = "SEQUENCE TIMEOUT WINDOW: ${draftRule.sequenceTimeoutMs}ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = NothingPureWhite
                        )
                        Slider(
                            value = draftRule.sequenceTimeoutMs.toFloat(),
                            onValueChange = { v ->
                                val snapped = ((v / 250f).roundToInt() * 250L).coerceIn(500L, 5000L)
                                onUpdateDraft { r -> r.copy(sequenceTimeoutMs = snapped) }
                            },
                            valueRange = 500f..5000f,
                            colors = SliderDefaults.colors(
                                thumbColor = NothingCrimsonRed,
                                activeTrackColor = NothingPureWhite
                            )
                        )
                    }

                    Button(
                        onClick = { openAccordionStep = 2 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingElevatedSurface,
                            contentColor = NothingPureWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        Text("NEXT: STEP 02 (WHAT TO SHOW)", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // STEP 2 ACCORDION: WHAT TO SHOW (MATRIX ACTION)
        item {
            AccordionStepContainer(
                stepNumber = "STEP 02",
                title = "What to show (Matrix Action)",
                summary = summarizeStep2(draftRule),
                isExpanded = openAccordionStep == 2,
                onToggle = { openAccordionStep = if (openAccordionStep == 2) 0 else 2 },
                testTag = "accordion_step_2"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "DYNAMIC REACTIVE MODES",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTechnicalGray
                    )

                    val isWaveform = draftRule.actionType == ActionType.AUDIO_WAVEFORM
                    val isRing = draftRule.actionType == ActionType.MATRIX_METER &&
                        draftRule.meterStyle == MeterStyle.PERIMETER_RING
                    val isBar = draftRule.actionType == ActionType.MATRIX_METER &&
                        draftRule.meterStyle == MeterStyle.HORIZONTAL_BAR

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NothingCompactPill(
                            label = "Audio Waveform Shader",
                            selected = isWaveform,
                            onClick = {
                                onUpdateDraft {
                                    it.copy(
                                        actionType = ActionType.AUDIO_WAVEFORM,
                                        targetAnimationName = "Audio Waveform Shader"
                                    )
                                }
                            }
                        )

                        NothingCompactPill(
                            label = "Battery Meter Ring",
                            selected = isRing,
                            onClick = {
                                onUpdateDraft {
                                    it.copy(
                                        actionType = ActionType.MATRIX_METER,
                                        meterStyle = MeterStyle.PERIMETER_RING
                                    )
                                }
                            }
                        )

                        NothingCompactPill(
                            label = "Progress Bar",
                            selected = isBar,
                            onClick = {
                                onUpdateDraft {
                                    it.copy(
                                        actionType = ActionType.MATRIX_METER,
                                        meterStyle = MeterStyle.HORIZONTAL_BAR
                                    )
                                }
                            }
                        )

                        NothingCompactPill(
                            label = "DRAW IN STUDIO",
                            selected = true,
                            leadingIcon = Icons.Default.Brush,
                            onClick = onDrawOnTheFly
                        )
                    }

                    HorizontalDivider(color = NothingBorderHairline)

                    Text(
                        text = "USER-CREATED CIRCULAR ANIMATIONS (${animations.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTechnicalGray
                    )

                    if (animations.isEmpty()) {
                        Surface(
                            color = NothingElevatedSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NothingBorderHairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No saved animations yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NothingPureWhite
                                )
                                Text(
                                    text = "Tap 'DRAW IN STUDIO' above to draw a custom 25×25 glyph or prompt the AI rasterizer (e.g. 'bear emoji', 'wifi logo'), then save it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NothingTechnicalGray
                                )
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(animations, key = { it.id }) { anim ->
                                val isSelected = (draftRule.actionType == ActionType.PLAY_ANIMATION ||
                                    draftRule.actionType == ActionType.SHOW_SINGLE_FRAME) &&
                                    draftRule.targetAnimationId == anim.id
                                Surface(
                                    color = NothingElevatedSurface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) NothingCrimsonRed else NothingBorderHairline
                                    ),
                                    modifier = Modifier
                                        .width(120.dp)
                                        .wrapContentHeight()
                                        .clickable {
                                            onUpdateDraft {
                                                it.copy(
                                                    actionType = ActionType.PLAY_ANIMATION,
                                                    targetAnimationId = anim.id,
                                                    targetAnimationName = anim.name
                                                )
                                            }
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        MiniMatrixThumbnail(
                                            pixels = anim.frames.firstOrNull()?.pixels ?: ByteArray(625),
                                            size = 52.dp,
                                            isSelected = isSelected
                                        )
                                        Text(
                                            text = anim.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) NothingCrimsonRed else NothingPureWhite,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { openAccordionStep = 3 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingElevatedSurface,
                            contentColor = NothingPureWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        Text("NEXT: STEP 03 (DURATION & BEHAVIOR)", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // STEP 3 ACCORDION: DURATION & BEHAVIOR
        item {
            AccordionStepContainer(
                stepNumber = "STEP 03",
                title = "Duration & Behavior",
                summary = summarizeStep3(draftRule),
                isExpanded = openAccordionStep == 3,
                onToggle = { openAccordionStep = if (openAccordionStep == 3) 0 else 3 },
                testTag = "accordion_step_3"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Option A: Until condition stops
                    Surface(
                        color = if (draftRule.holdUntilConditionClears)
                            NothingCrimsonRed.copy(alpha = 0.16f)
                        else
                            NothingElevatedSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (draftRule.holdUntilConditionClears) NothingCrimsonRed else NothingBorderHairline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .clickable { onUpdateDraft { it.copy(holdUntilConditionClears = true) } }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Until condition stops (Persistent 30 FPS)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NothingPureWhite
                                )
                                Text(
                                    text = "Keeps rear LEDs streaming continuously while audio plays or notification is active, and turns off immediately when dismissed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NothingTechnicalGray
                                )
                            }
                            Switch(
                                checked = draftRule.holdUntilConditionClears,
                                onCheckedChange = { onUpdateDraft { r -> r.copy(holdUntilConditionClears = it) } },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NothingPureWhite,
                                    checkedTrackColor = NothingCrimsonRed
                                )
                            )
                        }
                    }

                    // Option B: Fixed Duration
                    Surface(
                        color = if (!draftRule.holdUntilConditionClears)
                            NothingCrimsonRed.copy(alpha = 0.16f)
                        else
                            NothingElevatedSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (!draftRule.holdUntilConditionClears) NothingCrimsonRed else NothingBorderHairline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .clickable { onUpdateDraft { it.copy(holdUntilConditionClears = false) } }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Fixed Duration (${draftRule.overrideDurationMs / 1000.0} sec)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NothingPureWhite
                                )
                                if (!draftRule.holdUntilConditionClears) {
                                    NothingPillBadge(text = "ACTIVE", color = NothingCrimsonRed)
                                }
                            }

                            if (!draftRule.holdUntilConditionClears) {
                                Slider(
                                    value = draftRule.overrideDurationMs.toFloat(),
                                    onValueChange = { v ->
                                        val snapped = ((v / 500f).roundToInt() * 500L).coerceIn(1000L, 20_000L)
                                        onUpdateDraft { it.copy(overrideDurationMs = snapped) }
                                    },
                                    valueRange = 1000f..20_000f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = NothingCrimsonRed,
                                        activeTrackColor = NothingPureWhite
                                    )
                                )
                            }
                        }
                    }

                    // Priority Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RULE PRIORITY: P${draftRule.effectivePriority}",
                            style = MaterialTheme.typography.labelMedium,
                            color = NothingPureWhite
                        )
                        Text(
                            text = if (draftRule.essentialPriorityOverride)
                                "ESSENTIAL OVERRIDE"
                            else if (draftRule.priority >= 8)
                                "HIGH PRIORITY"
                            else
                                "STANDARD",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (draftRule.effectivePriority >= 8) NothingCrimsonRed else NothingTechnicalGray
                        )
                    }
                    Slider(
                        value = draftRule.effectivePriority.toFloat(),
                        onValueChange = { v ->
                            onUpdateDraft {
                                it.copy(
                                    priority = v.roundToInt(),
                                    essentialPriorityOverride = v.roundToInt() >= 10 && it.essentialPriorityOverride
                                )
                            }
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = NothingCrimsonRed,
                            activeTrackColor = NothingPureWhite
                        )
                    )
                }
            }
        }

        // Save Rule Primary CTA
        item {
            Button(
                onClick = onSaveRule,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingCrimsonRed,
                    contentColor = NothingPureWhite
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .testTag("builder_save_rule_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SAVE AUTOMATION RULE",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun AccordionStepContainer(
    stepNumber: String,
    title: String,
    summary: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = NothingCarbonSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpanded) NothingCrimsonRed else NothingBorderHairline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    NothingPillBadge(
                        text = stepNumber,
                        color = if (isExpanded) NothingCrimsonRed else NothingTechnicalGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NothingPureWhite
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTechnicalGray
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse Step" else "Expand Step",
                    tint = NothingPureWhite
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = NothingBorderHairline)
                    Spacer(modifier = Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

private fun summarizeStep1(rule: TriggerRule): String {
    return when (rule.conditionType) {
        ConditionType.NOTIFICATION -> {
            val extras = buildList {
                if (rule.senderFilterOperator != TextMatchOperator.ANY && rule.senderFilterPattern.isNotBlank()) {
                    add("Sender ${rule.senderFilterOperator.label}: '${rule.senderFilterPattern}'")
                }
                if (rule.bodyFilterOperator != TextMatchOperator.ANY && rule.bodyFilterPattern.isNotBlank()) {
                    add("Body ${rule.bodyFilterOperator.label}: '${rule.bodyFilterPattern}'")
                }
                if (rule.essentialPriorityOverride) {
                    add("Essential P10")
                }
            }
            if (extras.isEmpty()) {
                "Notification • ${rule.targetAppLabel}"
            } else {
                "Notification • ${rule.targetAppLabel} (${extras.joinToString(" • ")})"
            }
        }
        ConditionType.SYSTEM_STATE -> "System State Hardware Trigger"
        ConditionType.KEY_PRESS -> if (rule.sequenceEvents.isNotEmpty()) "Key Sequence: ${rule.sequenceEvents.joinToString(" → ")}" else "Key Press Trigger"
        ConditionType.MEDIA_PLAYBACK -> "Audio / Media Playing"
        ConditionType.BATTERY_LEVEL, ConditionType.CHARGING_STATE -> "Battery ${rule.numericThreshold}% (${rule.chargingRequirement.label})"
        ConditionType.TIMER_ALARM -> "Timer / Stopwatch"
        ConditionType.FACE_DOWN, ConditionType.SCREEN_STATE, ConditionType.LOW_POWER_MODE -> "Always-On Display Mode"
        ConditionType.BLUETOOTH_STATE,
        ConditionType.WIFI_STATE,
        ConditionType.RINGER_MODE,
        ConditionType.HOTSPOT_STATE,
        ConditionType.TORCH_STATE,
        ConditionType.AIRPLANE_MODE -> "${rule.conditionType.label} • ${rule.systemToggleFilter.label}"
    }
}

private fun summarizeStep2(rule: TriggerRule): String {
    return when (rule.actionType) {
        ActionType.AUDIO_WAVEFORM -> "Audio Waveform Shader (30 FPS)"
        ActionType.MATRIX_METER -> rule.meterStyle.label
        ActionType.PLAY_ANIMATION, ActionType.SHOW_SINGLE_FRAME -> "Animation: ${rule.targetAnimationName}"
        ActionType.GLYPH_SCRIPT_COMPOSITOR -> "GlyphScript v2 Multi-Layer"
    }
}

private fun summarizeStep3(rule: TriggerRule): String {
    return if (rule.holdUntilConditionClears || rule.essentialPriorityOverride) {
        "Until condition stops • Priority P${rule.effectivePriority}"
    } else {
        "Fixed ${rule.overrideDurationMs / 1000.0}s • Priority P${rule.effectivePriority}"
    }
}

@Composable
private fun InstalledAppPickerDialog(
    apps: List<InstalledAppOption>,
    selectedPackage: String,
    onSelectApp: (InstalledAppOption) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(apps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NothingCarbonSurface,
        titleContentColor = NothingPureWhite,
        textContentColor = NothingPureWhite,
        title = {
            Text(
                text = "SELECT INSTALLED APPLICATION",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Installed Apps",
                            tint = NothingTechnicalGray
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_picker_search_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NothingCrimsonRed,
                        unfocusedBorderColor = NothingBorderHairline,
                        focusedTextColor = NothingPureWhite,
                        unfocusedTextColor = NothingPureWhite
                    )
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = app.packageName == selectedPackage
                        Surface(
                            color = if (isSelected) NothingCrimsonRed.copy(alpha = 0.2f) else NothingElevatedSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) NothingCrimsonRed else NothingBorderHairline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clickable { onSelectApp(app) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AppIconBadge(
                                    packageName = app.packageName,
                                    appName = app.appName,
                                    sizeDp = 36
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = NothingPureWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NothingTechnicalGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = NothingPureWhite)
            }
        }
    )
}

@Composable
private fun AppIconBadge(
    packageName: String,
    appName: String,
    sizeDp: Int
) {
    val context = LocalContext.current
    val iconBitmap: ImageBitmap? = remember(packageName) {
        if (packageName == "*" || packageName.isBlank()) {
            null
        } else {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = appName,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NothingCarbonSurface)
        ) {
            Text(
                text = appName.take(1).uppercase().ifEmpty { "*" },
                style = MaterialTheme.typography.titleMedium,
                color = NothingCrimsonRed
            )
        }
    }
}
