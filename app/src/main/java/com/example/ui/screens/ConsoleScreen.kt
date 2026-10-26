package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.GlyphFrame
import com.example.domain.model.MATRIX_CELL_COUNT
import com.example.domain.model.TriggerRule
import com.example.dsl.GlyphScriptCompileError
import com.example.dsl.OFFICIAL_GLYPHSCRIPT_V2_SYSTEM_PROMPT
import com.example.ui.components.GlyphMatrix25Canvas
import com.example.ui.components.NothingCompactPill
import com.example.ui.components.NothingPillBadge
import com.example.ui.theme.NothingBorderHairline
import com.example.ui.theme.NothingCarbonSurface
import com.example.ui.theme.NothingCrimsonRed
import com.example.ui.theme.NothingElevatedSurface
import com.example.ui.theme.NothingMatrixGreen
import com.example.ui.theme.NothingPureBlack
import com.example.ui.theme.NothingPureWhite
import com.example.ui.theme.NothingTechnicalGray
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * State for the GlyphScript v2 Universal DSL Console tab.
 */
data class ConsoleUiState(
    val scriptInput: String = "",
    val compileError: GlyphScriptCompileError? = null,
    val allDiagnostics: List<com.example.dsl.GlyphScriptDiagnostic> = emptyList(),
    val lastCompiledRuleName: String? = null,
    val resolvedPackageBadge: String = "*",
    val resolvedAppLabel: String = "Any App (*)",
    val extractedVariablesPreview: Map<String, String> = emptyMap(),
    val dryRunVariableOverrides: Map<String, String> = emptyMap(),
    val dryRunPreviewPixels: ByteArray = ByteArray(MATRIX_CELL_COUNT),
    val dryRunPreviewFrames: List<GlyphFrame> = emptyList(),
    val dryRunTitle: String = "",
    val dryRunBody: String = "",
    val dryRunProgress: Int = 72,
    val dryRunGenerationId: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConsoleUiState) return false
        return scriptInput == other.scriptInput &&
            compileError == other.compileError &&
            allDiagnostics == other.allDiagnostics &&
            lastCompiledRuleName == other.lastCompiledRuleName &&
            resolvedPackageBadge == other.resolvedPackageBadge &&
            resolvedAppLabel == other.resolvedAppLabel &&
            extractedVariablesPreview == other.extractedVariablesPreview &&
            dryRunVariableOverrides == other.dryRunVariableOverrides &&
            dryRunPreviewFrames == other.dryRunPreviewFrames &&
            dryRunTitle == other.dryRunTitle &&
            dryRunBody == other.dryRunBody &&
            dryRunProgress == other.dryRunProgress &&
            dryRunGenerationId == other.dryRunGenerationId &&
            dryRunPreviewPixels.contentEquals(other.dryRunPreviewPixels)
    }

    override fun hashCode(): Int {
        var result = scriptInput.hashCode()
        result = 31 * result + (compileError?.hashCode() ?: 0)
        result = 31 * result + allDiagnostics.hashCode()
        result = 31 * result + (lastCompiledRuleName?.hashCode() ?: 0)
        result = 31 * result + resolvedPackageBadge.hashCode()
        result = 31 * result + resolvedAppLabel.hashCode()
        result = 31 * result + extractedVariablesPreview.hashCode()
        result = 31 * result + dryRunVariableOverrides.hashCode()
        result = 31 * result + dryRunPreviewFrames.hashCode()
        result = 31 * result + dryRunPreviewPixels.contentHashCode()
        result = 31 * result + dryRunTitle.hashCode()
        result = 31 * result + dryRunBody.hashCode()
        result = 31 * result + dryRunProgress
        result = 31 * result + dryRunGenerationId.hashCode()
        return result
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsoleScreen(
    consoleState: ConsoleUiState,
    existingRules: List<TriggerRule>,
    onUpdateScriptInput: (String) -> Unit,
    onPasteScript: (String) -> Unit,
    onClearScript: () -> Unit,
    onUndoClearScript: () -> Unit,
    onLoadTemplatePreset: (Int) -> Unit,
    onExportScratchpadBitmap: (String) -> Unit = {},
    onUpdateDryRunInputs: (title: String, body: String, progress: Int) -> Unit,
    onUpdateDryRunVariable: (varName: String, newValue: String) -> Unit = { _, _ -> },
    onDryRunScript: () -> Unit,
    onImportAndApplyScript: () -> Unit,
    onLoadRuleIntoConsole: (TriggerRule) -> Unit,
    onSystemPromptCopied: () -> Unit,
    onEditDryRunInStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current

    var showDryRunPayloadTuner by remember { mutableStateOf(true) }
    var showSystemPromptFullText by remember { mutableStateOf(false) }

    var localScriptText by remember { mutableStateOf(consoleState.scriptInput) }
    LaunchedEffect(consoleState.scriptInput) {
        if (localScriptText != consoleState.scriptInput) {
            localScriptText = consoleState.scriptInput
        }
    }

    var localTitleText by remember { mutableStateOf(consoleState.dryRunTitle) }
    LaunchedEffect(consoleState.dryRunTitle) {
        if (localTitleText != consoleState.dryRunTitle) {
            localTitleText = consoleState.dryRunTitle
        }
    }

    var localBodyText by remember { mutableStateOf(consoleState.dryRunBody) }
    LaunchedEffect(consoleState.dryRunBody) {
        if (localBodyText != consoleState.dryRunBody) {
            localBodyText = consoleState.dryRunBody
        }
    }

    val screenScrollState = rememberScrollState()
    val editorVerticalScrollState = rememberScrollState()
    val editorHorizontalScrollState = rememberScrollState()

    val lineCount = remember(localScriptText) {
        localScriptText.lines().size.coerceAtLeast(1)
    }
    val lineNumbersText = remember(lineCount) {
        (1..lineCount).joinToString("\n") { it.toString().padStart(2, ' ') }
    }

    // Continuous reactive preview loop keyed on content hash of all frames + dryRunGenerationId
    val previewFrames = consoleState.dryRunPreviewFrames
    val genId = consoleState.dryRunGenerationId
    val previewFramesContentKey = remember(genId, previewFrames, consoleState.dryRunPreviewPixels) {
        var h = genId.hashCode()
        h = 31 * h + consoleState.dryRunPreviewPixels.contentHashCode()
        h = 31 * h + previewFrames.size
        for (f in previewFrames) {
            h = 31 * h + f.pixels.contentHashCode()
            h = 31 * h + f.durationMs.hashCode()
        }
        h
    }

    var animatedPixels by remember {
        mutableStateOf(
            (previewFrames.firstOrNull()?.pixels ?: consoleState.dryRunPreviewPixels).copyOf()
        )
    }
    var activeKeyframeIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(previewFramesContentKey) {
        if (previewFrames.size <= 1) {
            val targetPixels = previewFrames.firstOrNull()?.pixels ?: consoleState.dryRunPreviewPixels
            animatedPixels = targetPixels.copyOf()
            activeKeyframeIndex = 0
            return@LaunchedEffect
        }
        var frameIdx = 0
        while (isActive) {
            val curr = previewFrames[frameIdx % previewFrames.size]
            activeKeyframeIndex = frameIdx % previewFrames.size
            animatedPixels = curr.pixels.copyOf()
            delay(curr.durationMs.coerceIn(33L, 500L))
            frameIdx = (frameIdx + 1) % previewFrames.size
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(screenScrollState)
            .fillMaxSize()
            .background(NothingPureBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("console_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Copyable LLM System Prompt Card with "AI Prompt" intent launcher
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NothingCarbonSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, NothingBorderHairline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = NothingCrimsonRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "AI Prompt Spec",
                                style = MaterialTheme.typography.titleMedium,
                                color = NothingPureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Generate rules with Gemini, ChatGPT, or Claude",
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTechnicalGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    NothingPillBadge(
                        text = "LLM SPEC",
                        color = NothingCrimsonRed
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val promptPayload = OFFICIAL_GLYPHSCRIPT_V2_SYSTEM_PROMPT
                            val clip = ClipData.newPlainText("GlyphScript v2 Prompt", promptPayload)
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(clip)
                            onSystemPromptCopied()

                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, promptPayload)
                            }
                            val chooserIntent = Intent.createChooser(sendIntent, "Generate GlyphScript with AI")
                            context.startActivity(chooserIntent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingCrimsonRed,
                            contentColor = NothingPureWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("generate_with_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI Prompt",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val clip = ClipData.newPlainText("GlyphScript v2 System Prompt", OFFICIAL_GLYPHSCRIPT_V2_SYSTEM_PROMPT)
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(clip)
                            onSystemPromptCopied()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NothingBorderHairline),
                        modifier = Modifier.testTag("copy_system_prompt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = NothingPureWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Copy Spec",
                            style = MaterialTheme.typography.labelMedium,
                            color = NothingPureWhite
                        )
                    }

                    OutlinedButton(
                        onClick = { showSystemPromptFullText = !showSystemPromptFullText },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NothingBorderHairline),
                        modifier = Modifier.testTag("toggle_system_prompt_preview")
                    ) {
                        Icon(
                            imageVector = if (showSystemPromptFullText) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle System Prompt View",
                            tint = NothingPureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = showSystemPromptFullText) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NothingPureBlack)
                            .border(1.dp, NothingBorderHairline, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .testTag("system_prompt_code_box")
                    ) {
                        Text(
                            text = OFFICIAL_GLYPHSCRIPT_V2_SYSTEM_PROMPT,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = NothingTechnicalGray
                            )
                        )
                    }
                }
            }
        }

        // 2. High-Contrast Red Error Banner (only when script is non-blank AND syntax/warnings/resolution fails)
        if ((consoleState.allDiagnostics.isNotEmpty() || consoleState.compileError != null) && consoleState.scriptInput.isNotBlank()) {
            val diagnostics = if (consoleState.allDiagnostics.isNotEmpty()) {
                consoleState.allDiagnostics
            } else {
                consoleState.compileError?.let { err ->
                    listOf(com.example.dsl.GlyphScriptDiagnostic("E101", err.line, err.column, err.reason))
                } ?: emptyList()
            }
            val hasErrors = diagnostics.any { it.severity == com.example.dsl.DiagnosticSeverity.ERROR }
            val bannerColor = if (hasErrors) NothingCrimsonRed else NothingTechnicalGray
            var errorCopied by remember(diagnostics) { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("console_error_banner"),
                color = bannerColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, bannerColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = bannerColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (hasErrors) "DIAGNOSTICS // FAILED (${diagnostics.size})" else "DIAGNOSTICS // WARNINGS (${diagnostics.size})",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = bannerColor
                            )
                        )
                    }
                    diagnostics.forEach { diag ->
                        Text(
                            text = diag.formattedMessage,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = NothingPureWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("console_error_message_text")
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val payload = diagnostics.joinToString("\n") { it.formattedMessage }
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("GlyphScript Diagnostics", payload)
                            )
                            errorCopied = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (errorCopied) NothingMatrixGreen else bannerColor
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = NothingPureBlack.copy(alpha = 0.55f),
                            contentColor = NothingPureWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("copy_compile_error_button")
                    ) {
                        Icon(
                            imageVector = if (errorCopied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                            contentDescription = "Copy Error",
                            tint = if (errorCopied) NothingMatrixGreen else bannerColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (errorCopied) "Copied Error" else "Copy Error",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (errorCopied) NothingMatrixGreen else NothingPureWhite
                        )
                    }
                }
            }
        }

        // 3. Script Editor Field with Action Controls PUSHED DOWN BELOW THE TEXT BOX
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NothingCarbonSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, NothingBorderHairline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row (Title & Validation Status)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Script Editor",
                            style = MaterialTheme.typography.titleMedium,
                            color = NothingPureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Lines: $lineCount • Resolver: Live",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTechnicalGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    NothingPillBadge(
                        text = if (consoleState.scriptInput.isBlank()) "Ready" else if (consoleState.compileError == null) "Valid" else "Err",
                        color = if (consoleState.scriptInput.isBlank()) NothingTechnicalGray else if (consoleState.compileError == null) NothingMatrixGreen else NothingCrimsonRed
                    )
                }

                // Line-numbered monospace editor container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NothingPureBlack)
                        .border(
                            width = 1.dp,
                            color = if (consoleState.compileError != null && consoleState.scriptInput.isNotBlank()) NothingCrimsonRed else NothingBorderHairline,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .verticalScroll(editorVerticalScrollState)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(editorHorizontalScrollState),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = lineNumbersText,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = NothingTechnicalGray
                            ),
                            modifier = Modifier
                                .padding(top = 14.dp, start = 4.dp, end = 6.dp)
                                .testTag("console_line_numbers")
                        )

                        OutlinedTextField(
                            value = localScriptText,
                            onValueChange = { newText ->
                                localScriptText = newText
                                onUpdateScriptInput(newText)
                            },
                            modifier = Modifier
                                .width(600.dp)
                                .testTag("console_script_editor_input"),
                            minLines = 7,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = NothingPureWhite
                            ),
                            placeholder = {
                                Text(
                                    text = "// Write GlyphScript v2 rule here...\nRULE \"Custom Rule\"\nWHEN notification FROM \"*\" ...",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = NothingTechnicalGray
                                    )
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NothingPureBlack,
                                unfocusedBorderColor = NothingPureBlack,
                                focusedContainerColor = NothingPureBlack,
                                unfocusedContainerColor = NothingPureBlack,
                                focusedTextColor = NothingPureWhite,
                                unfocusedTextColor = NothingPureWhite
                            )
                        )
                    }
                }

                // ACTION CONTROLS PUSHED DOWN BELOW THE TEXT BOX
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Paste Button
                    NothingCompactPill(
                        label = "Paste",
                        selected = false,
                        leadingIcon = Icons.Default.ContentPaste,
                        onClick = {
                            val text = clipboardManager.getText()?.text.orEmpty()
                            if (text.isNotBlank()) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                localScriptText = text
                                onPasteScript(text)
                            }
                        },
                        modifier = Modifier.testTag("editor_paste_button")
                    )

                    // Clear Button
                    NothingCompactPill(
                        label = "Clear",
                        selected = false,
                        leadingIcon = Icons.Default.Clear,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            localScriptText = ""
                            onClearScript()
                        },
                        modifier = Modifier.testTag("editor_clear_button")
                    )

                    // Undo Clear Button
                    NothingCompactPill(
                        label = "Undo",
                        selected = false,
                        leadingIcon = Icons.AutoMirrored.Filled.Undo,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onUndoClearScript()
                        },
                        modifier = Modifier.testTag("editor_undo_button")
                    )
                }

                // Primary Execution Buttons Below Action Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDryRunScript,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NothingPureWhite),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("console_dry_run_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = NothingPureWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Dry Run",
                            style = MaterialTheme.typography.labelMedium,
                            color = NothingPureWhite
                        )
                    }

                    Button(
                        onClick = onImportAndApplyScript,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingCrimsonRed,
                            contentColor = NothingPureWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("console_import_apply_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Import",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (existingRules.isNotEmpty()) {
                    HorizontalDivider(color = NothingBorderHairline)
                    Text(
                        text = "Saved Rules:",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTechnicalGray
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        existingRules.forEach { rule ->
                            NothingCompactPill(
                                label = rule.name,
                                selected = consoleState.lastCompiledRuleName == rule.name,
                                onClick = { onLoadRuleIntoConsole(rule) }
                            )
                        }
                    }
                }
            }
        }

        // 4. Multi-Layer Dry-Run Compositor Preview & Regex Extractor Inspector
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NothingCarbonSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, NothingBorderHairline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Dry Run Preview",
                            style = MaterialTheme.typography.titleMedium,
                            color = NothingPureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Target: ${consoleState.resolvedAppLabel} (${consoleState.resolvedPackageBadge})",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTechnicalGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = NothingMatrixGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (previewFrames.size > 1) {
                                "30 FPS (${activeKeyframeIndex + 1}/${previewFrames.size})"
                            } else {
                                "489 LEDS"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingMatrixGreen
                        )
                    }
                }

                // Left: 489-LED circular matrix preview | Right: Extracted variables & details
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    com.example.ui.components.CircularGlyphMatrixPreview(
                        pixels = animatedPixels,
                        modifier = Modifier
                            .size(128.dp)
                            .testTag("console_dry_run_canvas"),
                        interactive = false,
                        showCircularBezelOverlay = true
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Extracted Variables (Tap to Edit Live)",
                            style = MaterialTheme.typography.labelMedium,
                            color = NothingCrimsonRed
                        )
                        if (consoleState.extractedVariablesPreview.isEmpty()) {
                            Text(
                                text = "Tap [Dry Run] to evaluate EXTRACT regex patterns and composite layers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTechnicalGray
                            )
                        } else {
                            consoleState.extractedVariablesPreview.forEach { (k, v) ->
                                var localVarValue by remember(k, v) { mutableStateOf(v) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NothingPureBlack)
                                        .border(1.dp, NothingBorderHairline, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = k,
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NothingTechnicalGray
                                            ),
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        OutlinedTextField(
                                            value = localVarValue,
                                            onValueChange = { updated ->
                                                localVarValue = updated
                                                onUpdateDryRunVariable(k, updated)
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NothingPureWhite
                                            ),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NothingCrimsonRed,
                                                unfocusedBorderColor = NothingBorderHairline,
                                                focusedContainerColor = NothingCarbonSurface,
                                                unfocusedContainerColor = NothingCarbonSurface,
                                                cursorColor = NothingCrimsonRed
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("dry_run_var_input_$k")
                                        )
                                    }

                                    val lowerKey = k.lowercase()
                                    if (lowerKey.contains("maneuver") || lowerKey.contains("dir") || lowerKey.contains("turn")) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf("left", "right", "straight", "uturn").forEach { dirChip ->
                                                NothingCompactPill(
                                                    label = dirChip,
                                                    selected = localVarValue.equals(dirChip, ignoreCase = true),
                                                    onClick = {
                                                        localVarValue = dirChip
                                                        onUpdateDryRunVariable(k, dirChip)
                                                    },
                                                    fontSize = 9.sp,
                                                    horizontalPadding = 6.dp,
                                                    verticalPadding = 3.dp,
                                                    modifier = Modifier.testTag("dry_run_var_chip_${k}_$dirChip")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = if (previewFrames.size > 1) {
                                "Streaming ${previewFrames.size} keyframes @ 30 FPS"
                            } else {
                                "Static single-frame matrix composition"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTechnicalGray
                        )
                    }
                }

                OutlinedButton(
                    onClick = onEditDryRunInStudio,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NothingCrimsonRed),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NothingCrimsonRed.copy(alpha = 0.14f),
                        contentColor = NothingPureWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("console_edit_dry_run_in_builder_button")
                ) {
                    Text(
                        text = "EDIT IN BUILDER (PRESERVE DRY RUN STATE)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NothingPureWhite
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDryRunPayloadTuner = !showDryRunPayloadTuner }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Test Payload Inputs (Title / Text)",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTechnicalGray
                    )
                    Icon(
                        imageVector = if (showDryRunPayloadTuner) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand Payload Inputs",
                        tint = NothingTechnicalGray
                    )
                }

                AnimatedVisibility(visible = showDryRunPayloadTuner) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = localTitleText,
                            onValueChange = { newTitle ->
                                localTitleText = newTitle
                                onUpdateDryRunInputs(newTitle, localBodyText, consoleState.dryRunProgress)
                            },
                            label = { Text("Title") },
                            placeholder = { Text("e.g. Turn right in 200m", color = NothingTechnicalGray) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dry_run_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NothingCrimsonRed,
                                unfocusedBorderColor = NothingBorderHairline,
                                focusedTextColor = NothingPureWhite,
                                unfocusedTextColor = NothingPureWhite
                            )
                        )
                        OutlinedTextField(
                            value = localBodyText,
                            onValueChange = { newBody ->
                                localBodyText = newBody
                                onUpdateDryRunInputs(localTitleText, newBody, consoleState.dryRunProgress)
                            },
                            label = { Text("Text / Body") },
                            placeholder = { Text("e.g. 200m to destination", color = NothingTechnicalGray) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dry_run_body_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NothingCrimsonRed,
                                unfocusedBorderColor = NothingBorderHairline,
                                focusedTextColor = NothingPureWhite,
                                unfocusedTextColor = NothingPureWhite
                            )
                        )
                    }
                }
            }
        }
    }
}
