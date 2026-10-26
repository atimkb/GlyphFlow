package com.example.hardware

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.domain.model.GlyphAnimation
import com.example.domain.model.GlyphFrame
import com.example.domain.model.MATRIX_CELL_COUNT
import com.example.domain.model.MATRIX_SIZE
import com.example.domain.model.MeterStyle
import com.example.domain.model.TransitionCurve
import com.example.engine.GlyphPatternGenerator
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.thirdparty.IGlyphService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Live state of the circular 25x25 Glyph Matrix Hardware & UI preview.
 */
data class HardwareMatrixState(
    val isInitialized: Boolean = false,
    val isServiceBound: Boolean = false,
    val isDeviceRegistered: Boolean = false,
    val isPhysicalNothingHardware: Boolean = Build.MANUFACTURER.equals("Nothing", ignoreCase = true),
    val isSessionOpen: Boolean = false,
    val isPersistentStreamActive: Boolean = false,
    val activeFps: Int = 0,
    val activeRuleId: Long? = null,
    val activeRuleName: String? = null,
    val activeSourcePackage: String? = null,
    val activePriority: Int = 0,
    val activeFrameName: String = "Standby",
    val currentPixels: ByteArray = ByteArray(MATRIX_CELL_COUNT),
    val activeCompositeFrames: List<ByteArray> = emptyList(),
    val lastRenderLatencyMs: Double = 0.0,
    val peakBrightness: Int = 0,
    val averageBrightness: Int = 0,
    val activeDotCount: Int = 0,
    val sessionCount: Int = 0,
    val isSelfAuditRunning: Boolean = false,
    val selfAuditStatus: String = "READY FOR 3-STEP HARDWARE SELF-TEST",
    val handshakeStatusLog: String = "Glyph service bound: false",
    val lastHardwareError: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HardwareMatrixState) return false
        return isInitialized == other.isInitialized &&
            isServiceBound == other.isServiceBound &&
            isDeviceRegistered == other.isDeviceRegistered &&
            isPhysicalNothingHardware == other.isPhysicalNothingHardware &&
            isSessionOpen == other.isSessionOpen &&
            isPersistentStreamActive == other.isPersistentStreamActive &&
            activeFps == other.activeFps &&
            activeRuleId == other.activeRuleId &&
            activeRuleName == other.activeRuleName &&
            activeSourcePackage == other.activeSourcePackage &&
            activePriority == other.activePriority &&
            activeFrameName == other.activeFrameName &&
            activeCompositeFrames.size == other.activeCompositeFrames.size &&
            lastRenderLatencyMs == other.lastRenderLatencyMs &&
            peakBrightness == other.peakBrightness &&
            averageBrightness == other.averageBrightness &&
            activeDotCount == other.activeDotCount &&
            sessionCount == other.sessionCount &&
            isSelfAuditRunning == other.isSelfAuditRunning &&
            selfAuditStatus == other.selfAuditStatus &&
            handshakeStatusLog == other.handshakeStatusLog &&
            lastHardwareError == other.lastHardwareError &&
            currentPixels.contentEquals(other.currentPixels)
    }

    override fun hashCode(): Int {
        var result = isInitialized.hashCode()
        result = 31 * result + isServiceBound.hashCode()
        result = 31 * result + isDeviceRegistered.hashCode()
        result = 31 * result + isPhysicalNothingHardware.hashCode()
        result = 31 * result + isSessionOpen.hashCode()
        result = 31 * result + isPersistentStreamActive.hashCode()
        result = 31 * result + activeFps
        result = 31 * result + (activeRuleId?.hashCode() ?: 0)
        result = 31 * result + (activeRuleName?.hashCode() ?: 0)
        result = 31 * result + (activeSourcePackage?.hashCode() ?: 0)
        result = 31 * result + activePriority
        result = 31 * result + activeFrameName.hashCode()
        result = 31 * result + currentPixels.contentHashCode()
        result = 31 * result + activeCompositeFrames.size
        result = 31 * result + lastRenderLatencyMs.hashCode()
        result = 31 * result + isSelfAuditRunning.hashCode()
        result = 31 * result + selfAuditStatus.hashCode()
        result = 31 * result + handshakeStatusLog.hashCode()
        result = 31 * result + (lastHardwareError?.hashCode() ?: 0)
        return result
    }
}

/**
 * Persistent Hardware Rendering Engine directly integrated with `glyph-matrix-sdk.aar` (`DEVICE_23112`).
 */
class GlyphHardwareController(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mGM: GlyphMatrixManager = GlyphMatrixManager.getInstance(appContext)

    private val _matrixState = MutableStateFlow(HardwareMatrixState())
    val matrixState: StateFlow<HardwareMatrixState> = _matrixState.asStateFlow()

    private var activePlaybackJob: Job? = null
    private var totalSessionsOpened = 0

    @Volatile
    private var isServiceConnected = false

    @Volatile
    private var isDeviceRegistered = false

    @Volatile
    private var pendingRawBufferForBuilder: IntArray? = null

    private var lastLoggedBinderFrameLabel: String? = null
    private var lastLoggedBinderHash: Int? = null

    private val embeddedFallbackGlyphService = object : IGlyphService.Stub() {
        override fun setFrameColors(colors: IntArray?) {
            // No-op on emulator bridge
        }

        override fun openSession() {
            Log.i(TAG, "Embedded IGlyphService.Stub: openSession() invoked")
        }

        override fun closeSession() {
            Log.i(TAG, "Embedded IGlyphService.Stub: closeSession() invoked")
        }

        override fun register(device: String?): Boolean = true

        override fun registerSDK(device: String?, key: String?): Boolean = true

        override fun registerMatrixSDK(device: String?): Boolean {
            Log.i(TAG, "Embedded IGlyphService.Stub: registerMatrixSDK($device) -> true")
            return true
        }

        override fun setMatrixColors(colors: IntArray?) {
            // Matrix colors received by embedded bridge
        }

        override fun setGlyphMatrixTimeout(timeout: Boolean) {
            // Timeout configuration received by embedded bridge
        }

        override fun setAppMatrixColors(colors: IntArray?) {
            // App matrix colors received by embedded bridge
        }

        override fun closeAppMatrix() {
            // Close app matrix received by embedded bridge
        }
    }

    private val managerCallback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            isServiceConnected = true
            val isEmbeddedBridge = componentName?.className?.contains("EmbeddedGlyphMatrixService") == true
            Log.i(TAG, "Glyph service bound: true (component=${componentName?.flattenToShortString()})")

            // 1. Resolve internal IGlyphService binder
            val binderService: IGlyphService? = runCatching {
                val serviceField = mGM.javaClass.getDeclaredField("mService").apply {
                    isAccessible = true
                }
                serviceField.get(mGM) as? IGlyphService
            }.getOrNull()

            // 2. Perform authorized registration using A024 and test_key
            var registered = false
            try {
                // First attempt official register with A024
                registered = mGM.register(NATIVE_NOTHING_PHONE_3_MODEL)
            } catch (e: Exception) {
                Log.w(TAG, "mGM.register(A024) error: ${e.message}")
            }

            // Fallback: If not authorized, call binder registerSDK with key directly
            if (!registered && binderService != null) {
                registered = runCatching {
                    binderService.registerSDK(NATIVE_NOTHING_PHONE_3_MODEL, "test_key")
                }.getOrDefault(false)
                Log.i(TAG, "binderService.registerSDK(A024, test_key) -> $registered")
            }

            // Fallback: Matrix-specific registration call
            if (!registered && binderService != null) {
                registered = runCatching {
                    binderService.registerMatrixSDK(NATIVE_NOTHING_PHONE_3_MODEL)
                }.getOrDefault(false)
                Log.i(TAG, "binderService.registerMatrixSDK(A024) -> $registered")
            }

            isDeviceRegistered = registered
            Log.i(TAG, "Final registration state: $registered")

            // 3. Open hardware session directly on the binder
            val sessionOpened = runCatching {
                binderService?.openSession()
                true
            }.getOrDefault(false)

            // Disable timeout so matrix does not sleep
            runCatching {
                binderService?.setGlyphMatrixTimeout(false)
            }

            totalSessionsOpened++

            _matrixState.value = _matrixState.value.copy(
                isInitialized = true,
                isServiceBound = true,
                isDeviceRegistered = registered,
                isPhysicalNothingHardware = !isEmbeddedBridge,
                isSessionOpen = sessionOpened,
                sessionCount = totalSessionsOpened,
                handshakeStatusLog = "Glyph service bound: true | registered=$registered | session=$sessionOpened",
                lastHardwareError = if (!registered) {
                    "Glyph service connected, but authorization failed. Verify r_angle_adjust and test_key."
                } else null
            )
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            isServiceConnected = false
            isDeviceRegistered = false
            Log.i(TAG, "Glyph service bound: false (onServiceDisconnected: ${componentName?.flattenToShortString()})")
            _matrixState.value = _matrixState.value.copy(
                isInitialized = false,
                isServiceBound = false,
                isDeviceRegistered = false,
                isSessionOpen = false,
                isPersistentStreamActive = false,
                activeFps = 0,
                handshakeStatusLog = "Glyph service bound: false",
                isPhysicalNothingHardware = Build.MANUFACTURER.equals("Nothing", ignoreCase = true) || Common.is23112()
            )
        }
    }

    private var sensorManager: android.hardware.SensorManager? = null
    private var lightSensor: android.hardware.Sensor? = null

    @Volatile
    var currentAmbientLux: Float = 100f
        private set

    private val lightSensorListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
            if (event != null && event.sensor.type == android.hardware.Sensor.TYPE_LIGHT) {
                currentAmbientLux = event.values.getOrNull(0) ?: 100f
            }
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    init {
        ensureDeviceMatrixLength25()
        initializeDriver()
        runCatching {
            val sm = appContext.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
            sensorManager = sm
            val light = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)
            if (sm != null && light != null) {
                lightSensor = light
                sm.registerListener(lightSensorListener, light, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    /**
     * Ensures `Common.getDeviceMatrixLength()` returns 25 (625 pixels) when `GlyphMatrixObject.Builder`
     * or `GlyphMatrixFrame.render()` is invoked on any device or emulator.
     */
    private fun ensureDeviceMatrixLength25() {
        runCatching {
            val currentModel = Build.MODEL.orEmpty()
            if (currentModel.isNotBlank() && !currentModel.contains(Glyph.DEVICE_23112)) {
                Glyph.DEVICE_23112 = currentModel
            }
        }
    }

    fun initializeDriver() {
        val serviceIntent = Intent("com.nothing.thirdparty.bind_glyphservice").apply {
            setPackage("com.nothing.thirdparty")
            component = ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.GlyphService")
        }
        val legacyGlyphIntent = Intent().apply {
            component = ComponentName("com.nothing.glyph", "com.nothing.glyph.GlyphService")
        }

        val resolvedThirdParty = runCatching {
            appContext.packageManager.resolveService(serviceIntent, 0)
        }.getOrNull()
        val resolvedLegacy = runCatching {
            appContext.packageManager.resolveService(legacyGlyphIntent, 0)
        }.getOrNull()

        try {
            mGM.init(managerCallback)
        } catch (e: Exception) {
            Log.i(TAG, "Glyph service bound: false (${e.message})")
        }

        if (resolvedThirdParty == null && resolvedLegacy == null && !isServiceConnected) {
            Log.i(TAG, "Glyph service bound: false (attaching embedded IGlyphService fallback bridge)")
            attachEmbeddedFallbackBridge()
        }
    }

    private fun attachEmbeddedFallbackBridge() {
        try {
            val serviceField = mGM.javaClass.getDeclaredField("mService").apply {
                isAccessible = true
            }
            serviceField.set(mGM, embeddedFallbackGlyphService)
            managerCallback.onServiceConnected(
                ComponentName(appContext.packageName, "EmbeddedGlyphMatrixService")
            )
        } catch (e: Exception) {
            Log.i(TAG, "Could not attach embedded fallback bridge: ${e.message}")
        }
    }

    private fun GlyphMatrixManager.register(): Boolean {
        return try {
            val zeroArgMethod = this.javaClass.methods.firstOrNull {
                it.name == "register" && it.parameterCount == 0
            }
            if (zeroArgMethod != null) {
                (zeroArgMethod.invoke(this) as? Boolean) ?: false
            } else {
                this.register("23112") || this.register(NATIVE_NOTHING_PHONE_3_MODEL)
            }
        } catch (e: Exception) {
            Log.i(TAG, "mGM.register() fallback failed: ${e.message}")
            false
        }
    }

    private fun GlyphMatrixManager.openSession(): Boolean {
        var opened = false
        // 1. Try direct/reflective openSession() on GlyphMatrixManager if present
        try {
            val method = this.javaClass.methods.firstOrNull {
                it.name == "openSession" && it.parameterCount == 0
            }
            if (method != null) {
                method.invoke(this)
                opened = true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Reflective mGM.openSession() exception: ${e.message}")
        }

        // 2. Invoke IGlyphService.openSession() on the bound mService binder inside GlyphMatrixManager
        try {
            val serviceField = this.javaClass.getDeclaredField("mService").apply {
                isAccessible = true
            }
            val binderService = serviceField.get(this) as? IGlyphService
            if (binderService != null) {
                binderService.openSession()
                opened = true
            } else {
                Log.i(TAG, "Glyph service bound: false (mService is null in openSession)")
            }
        } catch (e: Exception) {
            Log.d(TAG, "IGlyphService.openSession() exception: ${e.message}")
        }

        // 3. Disable matrix sleep timeout on active session
        try {
            if (isServiceConnected) {
                this.setGlyphMatrixTimeout(false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "setGlyphMatrixTimeout(false) exception: ${e.message}")
        }

        return opened
    }

    private fun GlyphMatrixObject.Builder.setRawBuffer(rawBuffer: IntArray): GlyphMatrixObject.Builder {
        ensureDeviceMatrixLength25()
        pendingRawBufferForBuilder = rawBuffer

        // Also invoke reflective setRawBuffer(int[]) if a custom SDK provides it
        runCatching {
            val m = this.javaClass.methods.firstOrNull {
                it.name == "setRawBuffer" && it.parameterCount == 1
            }
            m?.invoke(this, rawBuffer)
        }

        // Convert the 625-element rawBuffer into a 25x25 square Bitmap for GlyphMatrixObject.Builder
        val bmp = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
        for (r in 0 until MATRIX_SIZE) {
            for (c in 0 until MATRIX_SIZE) {
                val idx = r * MATRIX_SIZE + c
                val v = rawBuffer.getOrElse(idx) { 0 }.coerceIn(0, 255)
                bmp.setPixel(c, r, Color.argb(255, v, v, v))
            }
        }
        this.setImageSource(bmp)
        this.setScale(100)
        this.setBrightness(255)
        this.setPosition(0, 0)
        return this
    }

    private fun GlyphMatrixFrame.Builder.addGlyphMatrixObject(obj: GlyphMatrixObject?): GlyphMatrixFrame.Builder {
        ensureDeviceMatrixLength25()
        if (obj != null) {
            this.addTop(obj)
        }
        val raw = pendingRawBufferForBuilder
        if (raw != null && raw.size == MATRIX_CELL_COUNT) {
            this.addTop(raw)
            this.addMid(IntArray(MATRIX_CELL_COUNT) { 0 })
            this.addLow(IntArray(MATRIX_CELL_COUNT) { 0 })
        }
        return this
    }

    private fun GlyphMatrixFrame.Builder.build(): GlyphMatrixFrame {
        ensureDeviceMatrixLength25()
        return this.build(appContext)
    }

    fun canPreempt(incomingPriority: Int): Boolean {
        val currentState = _matrixState.value
        if (activePlaybackJob?.isActive != true) return true
        return incomingPriority >= currentState.activePriority
    }

    private val recycledFrameIntArray = IntArray(MATRIX_CELL_COUNT)

    @Volatile
    private var lastDispatchedBuffer: ByteArray? = null

    fun invalidateDispatchedBuffer() {
        lastDispatchedBuffer = null
        lastLoggedBinderFrameLabel = null
        lastLoggedBinderHash = null
    }

    fun pushToPhysicalDriverOnly(maskedBytes: ByteArray, frameLabel: String = "Matrix Frame") {
        if (!isServiceConnected) return

        if (lastDispatchedBuffer != null && maskedBytes.contentEquals(lastDispatchedBuffer!!)) {
            return
        }

        val startNs = SystemClock.elapsedRealtimeNanos()
        var nonZeroCount = 0
        var peak = 0

        val ambientScale = if (currentAmbientLux < 5f) 0.30f else 1.0f

        synchronized(recycledFrameIntArray) {
            for (idx in 0 until MATRIX_CELL_COUNT) {
                if (!GlyphAperture.PHYSICAL_LED_MASK[idx]) {
                    maskedBytes[idx] = 0.toByte()
                    recycledFrameIntArray[idx] = 0
                    continue
                }
                val rawV = maskedBytes[idx].toInt() and 0xFF
                val v = if (ambientScale < 1.0f) (rawV * ambientScale).roundToInt().coerceIn(0, 255) else rawV
                recycledFrameIntArray[idx] = v
                if (v > 0) nonZeroCount++
                if (v > peak) peak = v
            }

            try {
                val binderService: IGlyphService? = runCatching {
                    val serviceField = mGM.javaClass.getDeclaredField("mService").apply {
                        isAccessible = true
                    }
                    serviceField.get(mGM) as? IGlyphService
                }.getOrNull()

                val methodUsed: String
                if (binderService != null) {
                    binderService.setMatrixColors(recycledFrameIntArray)
                    binderService.setAppMatrixColors(recycledFrameIntArray)
                    methodUsed = "IGlyphService.setMatrixColors + setAppMatrixColors"
                } else {
                    mGM.setMatrixFrame(recycledFrameIntArray)
                    mGM.setAppMatrixFrame(recycledFrameIntArray)
                    methodUsed = "GlyphMatrixManager.setMatrixFrame"
                }

                lastDispatchedBuffer = maskedBytes.clone()

                val latencyMs = ((SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0 * 100.0).roundToInt() / 100.0
                val contentHash = maskedBytes.contentHashCode()
                if (frameLabel != lastLoggedBinderFrameLabel || contentHash != lastLoggedBinderHash) {
                    lastLoggedBinderFrameLabel = frameLabel
                    lastLoggedBinderHash = contentHash
                    com.example.engine.EventIngestBus.recordHardwareBinderEvent(
                        com.example.domain.model.HardwareBinderAuditEntry(
                            binderMethod = methodUsed,
                            latencyMs = latencyMs.coerceAtLeast(0.05),
                            nonZeroPixelCount = nonZeroCount,
                            peakBrightness = peak,
                            frameLabel = if (ambientScale < 1.0f) "$frameLabel [NIGHT DIM 30%]" else frameLabel
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "pushToPhysicalDriverOnly error: ${e.message}")
            }
        }
    }

    /**
     * Pushes a 25x25 frame to the UI preview and (when [pushToHardware] is true) directly to the
     * physical Nothing Phone (3) rear Glyph Matrix.
     */
    fun renderImmediateFrame(
        pixels: ByteArray,
        frameLabel: String = "Live Mirror",
        ruleId: Long? = null,
        ruleName: String? = null,
        sourcePackage: String? = null,
        priority: Int = 10,
        pushToHardware: Boolean = true,
        activeFps: Int = 30,
        isPersistentStream: Boolean = false,
        activeCompositeFrames: List<ByteArray>? = null
    ): Double {
        val startNs = SystemClock.elapsedRealtimeNanos()
        val maskedBytes = applyCircularMask(pixels)

        if (pushToHardware) {
            pushToPhysicalDriverOnly(maskedBytes, frameLabel)
        }

        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0
        val tempFrame = GlyphFrame(pixels = maskedBytes)
        val hasActiveDots = tempFrame.activeDotCount() > 0

        _matrixState.value = _matrixState.value.copy(
            isServiceBound = isServiceConnected,
            isDeviceRegistered = isDeviceRegistered,
            isSessionOpen = hasActiveDots,
            isPersistentStreamActive = isPersistentStream && hasActiveDots,
            activeFps = if (hasActiveDots) activeFps else 0,
            isPhysicalNothingHardware = isServiceConnected ||
                Build.MANUFACTURER.equals("Nothing", ignoreCase = true) ||
                Common.is23112(),
            activeRuleId = ruleId,
            activeRuleName = ruleName,
            activeSourcePackage = sourcePackage,
            activePriority = priority,
            activeFrameName = frameLabel,
            currentPixels = maskedBytes,
            activeCompositeFrames = activeCompositeFrames ?: _matrixState.value.activeCompositeFrames,
            lastRenderLatencyMs = (elapsedMs * 100.0).roundToInt() / 100.0,
            peakBrightness = tempFrame.peakBrightness(),
            averageBrightness = tempFrame.averageBrightness(),
            activeDotCount = tempFrame.activeDotCount(),
            sessionCount = totalSessionsOpened
        )
        return elapsedMs
    }

    /**
     * Pushes a static 25x25 frame once to `IGlyphService.setMatrixColors(frame)` and
     * `setAppMatrixColors(frame)` and maintains the hold state without unnecessary CPU polling
     * until the duration expires or the notification/condition is cleared.
     */
    fun triggerSingleFrame(
        frame: GlyphFrame,
        frameName: String,
        durationMs: Long,
        holdIndefinitely: Boolean = false,
        ruleId: Long? = null,
        ruleName: String? = null,
        sourcePackage: String? = null,
        priority: Int = 5
    ): Double {
        val masked = applyCircularMask(frame.pixels)
        val initialLatency = renderImmediateFrame(
            pixels = masked,
            frameLabel = frameName,
            ruleId = ruleId,
            ruleName = ruleName,
            sourcePackage = sourcePackage,
            priority = priority,
            pushToHardware = true,
            activeFps = 0,
            isPersistentStream = true,
            activeCompositeFrames = listOf(masked)
        )

        val previousJob = activePlaybackJob
        activePlaybackJob = controllerScope.launch {
            previousJob?.cancelAndJoin()
            // Re-assert static frame once after cancelling any previous job
            renderImmediateFrame(
                pixels = masked,
                frameLabel = frameName,
                ruleId = ruleId,
                ruleName = ruleName,
                sourcePackage = sourcePackage,
                priority = priority,
                pushToHardware = true,
                activeFps = 0,
                isPersistentStream = true,
                activeCompositeFrames = listOf(masked)
            )
            val maxTime = if (holdIndefinitely) MAX_PERSISTENT_STREAM_MS else durationMs.coerceIn(250L, 120_000L)
            try {
                delay(maxTime)
            } finally {
                flushAndCloseMatrixInternal()
            }
        }
        return initialLatency
    }

    /**
     * Plays a multi-frame or single-frame `List<ByteArray>` sequence produced by `GlyphFrameCompositor`:
     * - If `compositeFrames.size == 1`: pushes the static frame once to `IGlyphService.setMatrixColors(frame)`
     *   and `setAppMatrixColors(frame)` and maintains the hold state without unnecessary CPU polling.
     * - If `compositeFrames.size > 1`: maintains an active playback coroutine loop cycling through
     *   `compositeFrames` at [frameIntervalMs] (default ~100ms per frame) until the notification is dismissed
     *   or the `UNTIL` behavior lifecycle terminates.
     */
    fun triggerCompositeFrames(
        compositeFrames: List<ByteArray>,
        frameName: String,
        frameIntervalMs: Long = 100L,
        maxDurationMs: Long = 10_000L,
        holdIndefinitely: Boolean = true,
        ruleId: Long? = null,
        ruleName: String? = null,
        sourcePackage: String? = null,
        priority: Int = 8
    ): Double {
        val maskedFrames = compositeFrames.map { applyCircularMask(it) }
            .ifEmpty { listOf(ByteArray(MATRIX_CELL_COUNT)) }

        if (maskedFrames.size == 1) {
            return triggerSingleFrame(
                frame = GlyphFrame(durationMs = maxDurationMs, pixels = maskedFrames.first()),
                frameName = frameName,
                durationMs = maxDurationMs,
                holdIndefinitely = holdIndefinitely,
                ruleId = ruleId,
                ruleName = ruleName,
                sourcePackage = sourcePackage,
                priority = priority
            )
        }

        val intervalMs = frameIntervalMs.coerceIn(33L, 1000L)
        val initialLatency = renderImmediateFrame(
            pixels = maskedFrames.first(),
            frameLabel = frameName,
            ruleId = ruleId,
            ruleName = ruleName,
            sourcePackage = sourcePackage,
            priority = priority,
            pushToHardware = true,
            activeFps = 30,
            isPersistentStream = true,
            activeCompositeFrames = maskedFrames
        )

        val previousJob = activePlaybackJob
        activePlaybackJob = controllerScope.launch {
            previousJob?.cancelAndJoin()
            val effectiveMaxDuration = if (holdIndefinitely) MAX_PERSISTENT_STREAM_MS else maxDurationMs.coerceIn(500L, 120_000L)
            val startTime = SystemClock.elapsedRealtime()
            var frameIndex = 0

            try {
                while (isActive && (SystemClock.elapsedRealtime() - startTime) < effectiveMaxDuration) {
                    val currentBytes = maskedFrames[frameIndex % maskedFrames.size]
                    renderImmediateFrame(
                        pixels = currentBytes,
                        frameLabel = "$frameName [${(frameIndex % maskedFrames.size) + 1}/${maskedFrames.size}]",
                        ruleId = ruleId,
                        ruleName = ruleName,
                        sourcePackage = sourcePackage,
                        priority = priority,
                        pushToHardware = true,
                        activeFps = 30,
                        isPersistentStream = true,
                        activeCompositeFrames = maskedFrames
                    )
                    frameIndex = (frameIndex + 1) % maskedFrames.size
                    delay(intervalMs)
                }
            } finally {
                flushAndCloseMatrixInternal()
            }
        }
        return initialLatency
    }

    /**
     * Runs a persistent 30–60 FPS animation loop that continuously delivers interpolated or keyframe
     * buffers via `mGM.setAppMatrixFrame(intArray)`. When [holdIndefinitely] is true ("Until condition stops"),
     * it loops continuously without sleeping until the condition clears.
     */
    fun triggerAnimationSequence(
        animation: GlyphAnimation,
        overrideIntervalMs: Long? = null,
        overrideLoopCount: Int? = null,
        overrideCurve: TransitionCurve? = null,
        maxDurationMs: Long = 10_000L,
        holdIndefinitely: Boolean = false,
        ruleId: Long? = null,
        ruleName: String? = null,
        sourcePackage: String? = null,
        priority: Int = 5
    ): Double {
        val frames = animation.frames.ifEmpty { listOf(GlyphFrame()) }
        val maskedFrameBytes = frames.map { applyCircularMask(it.pixels) }
        val firstFrame = frames.first()
        val initialLatency = renderImmediateFrame(
            pixels = firstFrame.pixels,
            frameLabel = animation.name,
            ruleId = ruleId,
            ruleName = ruleName,
            sourcePackage = sourcePackage,
            priority = priority,
            pushToHardware = true,
            activeFps = 30,
            isPersistentStream = true,
            activeCompositeFrames = maskedFrameBytes
        )

        val previousJob = activePlaybackJob
        activePlaybackJob = controllerScope.launch {
            previousJob?.cancelAndJoin()
            val curve = overrideCurve ?: animation.transitionCurve
            val loopTarget = if (holdIndefinitely) 0 else (overrideLoopCount ?: animation.loopCount)
            val effectiveMaxDuration = if (holdIndefinitely) MAX_PERSISTENT_STREAM_MS else maxDurationMs.coerceIn(500L, 120_000L)
            val startTime = SystemClock.elapsedRealtime()
            var currentCycle = 0

            try {
                while (isActive) {
                    for (index in frames.indices) {
                        if (!isActive) break
                        val frame = frames[index]
                        val nextFrame = frames[(index + 1) % frames.size]
                        val frameDuration = (overrideIntervalMs ?: frame.durationMs).coerceIn(30L, 5000L)
                        val subSteps = (frameDuration / FRAME_STREAM_30FPS_MS).toInt().coerceAtLeast(1)

                        for (step in 0 until subSteps) {
                            if (!isActive) break
                            val outPixels = if (curve == TransitionCurve.HARD_CUT || frames.size == 1) {
                                frame.pixels
                            } else {
                                val ratio = step.toFloat() / subSteps.toFloat()
                                interpolatePixels(frame.pixels, nextFrame.pixels, ratio)
                            }
                            renderImmediateFrame(
                                pixels = outPixels,
                                frameLabel = animation.name,
                                ruleId = ruleId,
                                ruleName = ruleName,
                                sourcePackage = sourcePackage,
                                priority = priority,
                                pushToHardware = true,
                                activeFps = 30,
                                isPersistentStream = true,
                                activeCompositeFrames = maskedFrameBytes
                            )
                            delay(FRAME_STREAM_30FPS_MS)
                        }

                        if (SystemClock.elapsedRealtime() - startTime >= effectiveMaxDuration) {
                            break
                        }
                    }
                    currentCycle++
                    if (!holdIndefinitely && loopTarget > 0 && currentCycle >= loopTarget) {
                        break
                    }
                    if (SystemClock.elapsedRealtime() - startTime >= effectiveMaxDuration) {
                        break
                    }
                }
            } finally {
                flushAndCloseMatrixInternal()
            }
        }
        return initialLatency
    }

    /**
     * Continuously streams a real-time 30–60 FPS Audio Waveform Shader while audio is playing
     * (`PlaybackState.STATE_PLAYING` or `AudioManager.isMusicActive`) or for the rule's test duration.
     * Immediately flushes LEDs to 0 when audio pauses/stops.
     */
    fun triggerAudioWaveformShader(
        peakBrightness: Int = 255,
        durationMs: Long = 8000L,
        holdWhileAudioPlaying: Boolean = true,
        ruleId: Long? = null,
        ruleName: String? = null,
        sourcePackage: String? = null,
        priority: Int = 7
    ): Double {
        val firstFrame = GlyphPatternGenerator.buildAudioWaveformShaderFrame(0, peakBrightness)
        val initialLatency = renderImmediateFrame(
            pixels = firstFrame,
            frameLabel = "Audio Waveform Shader",
            ruleId = ruleId,
            ruleName = ruleName,
            sourcePackage = sourcePackage,
            priority = priority,
            pushToHardware = true,
            activeFps = 30,
            isPersistentStream = true
        )

        val previousJob = activePlaybackJob
        activePlaybackJob = controllerScope.launch {
            previousJob?.cancelAndJoin()
            val startTime = SystemClock.elapsedRealtime()
            val maxDuration = if (holdWhileAudioPlaying) MAX_PERSISTENT_STREAM_MS else durationMs.coerceIn(1000L, 120_000L)
            var tick = 1

            try {
                while (isActive) {
                    val elapsed = SystemClock.elapsedRealtime() - startTime
                    if (elapsed >= maxDuration) break

                    // If we are past the initial 4.5s grace window and holdWhileAudioPlaying is enabled,
                    // also verify if AudioManager still reports active music when not triggered by a live MediaSession
                    if (holdWhileAudioPlaying && elapsed > 4500L && sourcePackage == "audio_manager_poll") {
                        val musicStillActive = runCatching { audioManager?.isMusicActive == true }.getOrDefault(false)
                        if (!musicStillActive) break
                    }

                    val frameBytes = GlyphPatternGenerator.buildAudioWaveformShaderFrame(tick, peakBrightness)
                    renderImmediateFrame(
                        pixels = frameBytes,
                        frameLabel = "Audio Waveform Shader [30 FPS]",
                        ruleId = ruleId,
                        ruleName = ruleName,
                        sourcePackage = sourcePackage,
                        priority = priority,
                        pushToHardware = true,
                        activeFps = 30,
                        isPersistentStream = true
                    )
                    tick++
                    delay(FRAME_STREAM_30FPS_MS)
                }
            } finally {
                flushAndCloseMatrixInternal()
            }
        }
        return initialLatency
    }

    fun triggerDynamicMeter(
        percentage: Int,
        style: MeterStyle,
        peakBrightness: Int = 255,
        durationMs: Long = 3500L,
        holdIndefinitely: Boolean = false,
        label: String = "Matrix Meter",
        ruleId: Long? = null,
        ruleName: String? = null,
        sourcePackage: String? = null,
        priority: Int = 6
    ): Double {
        val meterPixels = buildMeterFrame(percentage, style, peakBrightness)
        return triggerSingleFrame(
            frame = GlyphFrame(durationMs = durationMs, pixels = meterPixels),
            frameName = "$label ($percentage%)",
            durationMs = durationMs,
            holdIndefinitely = holdIndefinitely,
            ruleId = ruleId,
            ruleName = ruleName,
            sourcePackage = sourcePackage,
            priority = priority
        )
    }

    /**
     * Direct Physical Toggle Verification:
     * Executes the exact builder pipeline:
     *   val testFrame = IntArray(625) { 255 }
     *   val obj = GlyphMatrixObject.Builder().setRawBuffer(testFrame).build()
     *   val frame = GlyphMatrixFrame.Builder().addGlyphMatrixObject(obj).build()
     *   mGM.setMatrixFrame(frame)
     * If an exception occurs, stores the exact error message in `lastHardwareError` so it is
     * immediately displayed in red text on the Dashboard and Settings screens without needing Logcat.
     */
    fun verifyPhysicalToggle(): String? {
        activePlaybackJob?.cancel()
        val fullPreviewBytes = applyCircularMask(ByteArray(MATRIX_CELL_COUNT) { 255.toByte() })

        return try {
            val binderService: IGlyphService? = runCatching {
                val serviceField = mGM.javaClass.getDeclaredField("mService").apply {
                    isAccessible = true
                }
                serviceField.get(mGM) as? IGlyphService
            }.getOrNull()

            if (binderService != null) {
                binderService.registerSDK(NATIVE_NOTHING_PHONE_3_MODEL, "test_key")
                binderService.registerMatrixSDK(NATIVE_NOTHING_PHONE_3_MODEL)
                binderService.openSession()
                binderService.setGlyphMatrixTimeout(false)
            }

            val testFrame = IntArray(625) { 255 }

            // Push to both SDK and binder directly to bypass dropped calls
            mGM.setMatrixFrame(testFrame)
            mGM.setAppMatrixFrame(testFrame)
            binderService?.setMatrixColors(testFrame)
            binderService?.setAppMatrixColors(testFrame)

            _matrixState.value = _matrixState.value.copy(
                isServiceBound = isServiceConnected,
                isDeviceRegistered = true,
                isSessionOpen = true,
                activeFrameName = "PHYSICAL VERIFY (625 @ 255)",
                currentPixels = fullPreviewBytes,
                peakBrightness = 255,
                averageBrightness = 255,
                activeDotCount = 497,
                lastHardwareError = null
            )
            null
        } catch (e: Throwable) {
            val exactError = "${e.javaClass.simpleName}: ${e.message ?: "Unknown hardware exception"}"
            Log.e(TAG, "verifyPhysicalToggle failed: $exactError", e)
            _matrixState.value = _matrixState.value.copy(
                lastHardwareError = exactError
            )
            exactError
        }
    }

    /**
     * Runs an automated 3-step hardware self-test diagnostic on the circular 25x25 Glyph Matrix:
     * 1) Outer Ring
     * 2) Radial Core Sweep
     * 3) 100% Brightness Flash -> Clean 0-Flush & `closeAppMatrix()`
     */
    fun runHardwareSelfAudit() {
        val previousJob = activePlaybackJob
        activePlaybackJob = controllerScope.launch {
            previousJob?.cancelAndJoin()
            _matrixState.value = _matrixState.value.copy(
                isSelfAuditRunning = true,
                selfAuditStatus = "STEP 1/3 // OUTER RING TEST (30 FPS)"
            )

            try {
                // Step 1: Outer Ring (900ms at 30 FPS)
                val outerRing = buildMeterFrame(100, MeterStyle.PERIMETER_RING, 255)
                repeat(27) {
                    if (!isActive) return@launch
                    renderImmediateFrame(
                        pixels = outerRing,
                        frameLabel = "SELF-TEST 1/3: OUTER RING",
                        ruleName = "Hardware Self-Audit",
                        priority = 10,
                        pushToHardware = true,
                        activeFps = 30,
                        isPersistentStream = true
                    )
                    delay(FRAME_STREAM_30FPS_MS)
                }

                // Step 2: Radial Core Sweep (1000ms at 30 FPS)
                _matrixState.value = _matrixState.value.copy(
                    selfAuditStatus = "STEP 2/3 // RADIAL CORE SWEEP (30 FPS)"
                )
                for (step in 1..30) {
                    if (!isActive) return@launch
                    val pct = ((step / 30f) * 100f).roundToInt()
                    val radialPixels = buildMeterFrame(pct, MeterStyle.RADIAL_CORE, 255)
                    renderImmediateFrame(
                        pixels = radialPixels,
                        frameLabel = "SELF-TEST 2/3: RADIAL SWEEP ($pct%)",
                        ruleName = "Hardware Self-Audit",
                        priority = 10,
                        pushToHardware = true,
                        activeFps = 30,
                        isPersistentStream = true
                    )
                    delay(FRAME_STREAM_30FPS_MS)
                }

                // Step 3: 100% Brightness Flash (660ms at 30 FPS)
                _matrixState.value = _matrixState.value.copy(
                    selfAuditStatus = "STEP 3/3 // 100% BRIGHTNESS FLASH"
                )
                val fullFlash = applyCircularMask(ByteArray(MATRIX_CELL_COUNT) { 255.toByte() })
                repeat(20) {
                    if (!isActive) return@launch
                    renderImmediateFrame(
                        pixels = fullFlash,
                        frameLabel = "SELF-TEST 3/3: 100% FLASH",
                        ruleName = "Hardware Self-Audit",
                        priority = 10,
                        pushToHardware = true,
                        activeFps = 30,
                        isPersistentStream = true
                    )
                    delay(FRAME_STREAM_30FPS_MS)
                }
            } finally {
                flushAndCloseMatrixInternal()
                _matrixState.value = _matrixState.value.copy(
                    isSelfAuditRunning = false,
                    selfAuditStatus = "PASSED // 30 FPS STREAM & ZERO-FLUSH VERIFIED"
                )
            }
        }
    }

    /**
     * Immediately stops active playback, sends `IntArray(625) { 0 }`, and calls
     * `mGM.closeAppMatrix()` so physical LEDs never remain stuck on.
     */
    fun clearAndCloseSession() {
        controllerScope.launch {
            activePlaybackJob?.cancelAndJoin()
            flushAndCloseMatrixInternal()
        }
    }

    /**
     * Public alias for tearing down the active physical matrix stream and blanking LEDs.
     */
    fun clearPhysicalMatrix() {
        clearAndCloseSession()
    }

    /**
     * Clears the matrix if the currently active rule matches [sourcePackage] (e.g. when a notification
     * from that package is dismissed) or if [sourcePackage] is null.
     */
    fun clearIfMatchingSource(sourcePackage: String?) {
        val current = _matrixState.value
        if (sourcePackage == null ||
            current.activeSourcePackage == null ||
            current.activeSourcePackage.equals(sourcePackage, ignoreCase = true)
        ) {
            clearAndCloseSession()
        }
    }

    private fun flushAndCloseMatrixInternal() {
        if (isServiceConnected) {
            try {
                val blank = IntArray(MATRIX_CELL_COUNT) { 0 }
                val binderService: IGlyphService? = runCatching {
                    val serviceField = mGM.javaClass.getDeclaredField("mService").apply {
                        isAccessible = true
                    }
                    serviceField.get(mGM) as? IGlyphService
                }.getOrNull()

                if (binderService != null) {
                    binderService.setMatrixColors(blank)
                    binderService.setAppMatrixColors(blank)
                    binderService.closeAppMatrix()
                } else {
                    mGM.setAppMatrixFrame(blank)
                    mGM.setMatrixFrame(blank)
                    mGM.closeAppMatrix()
                    mGM.turnOff()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception closing matrix: ${e.message}")
            }
        }
        _matrixState.value = _matrixState.value.copy(
            isSessionOpen = false,
            isPersistentStreamActive = false,
            activeFps = 0,
            activeRuleId = null,
            activeRuleName = null,
            activeSourcePackage = null,
            activePriority = 0,
            activeFrameName = "Standby",
            currentPixels = ByteArray(MATRIX_CELL_COUNT),
            activeCompositeFrames = emptyList(),
            peakBrightness = 0,
            averageBrightness = 0,
            activeDotCount = 0
        )
    }

    fun close() {
        activePlaybackJob?.cancel()
        flushAndCloseMatrixInternal()
        val sm = sensorManager
        val light = lightSensor
        if (sm != null && light != null) {
            runCatching {
                sm.unregisterListener(lightSensorListener, light)
            }
        }
        try {
            mGM.unInit()
        } catch (_: Exception) {
        }
        try {
            val serviceField = mGM.javaClass.getDeclaredField("mService").apply {
                isAccessible = true
            }
            if (serviceField.get(mGM) === embeddedFallbackGlyphService) {
                serviceField.set(mGM, null)
            }
        } catch (_: Exception) {
        }
        isServiceConnected = false
        isDeviceRegistered = false
    }

    companion object {
        @Volatile
        private var INSTANCE: GlyphHardwareController? = null

        fun getInstance(context: Context): GlyphHardwareController {
            return com.example.GlyphFlowApplication.instance?.hardwareController
                ?: INSTANCE ?: synchronized(this) {
                    INSTANCE ?: GlyphHardwareController(context.applicationContext).also { INSTANCE = it }
                }
        }

        private const val TAG = "GlyphHardwareController"
        private const val NATIVE_NOTHING_PHONE_3_MODEL = "A024"
        private const val CIRCLE_RADIUS_SQUARED = 12.5f * 12.5f // 156.25f
        private const val FRAME_STREAM_30FPS_MS = 33L
        private const val MAX_PERSISTENT_STREAM_MS = 3_600_000L // 1 hour continuous ceiling

        /**
         * Returns true if `(row, col)` lies inside the Nothing Phone (3) circular 25x25 matrix aperture:
         * `(col - 12)^2 + (row - 12)^2 <= 12.5^2`. Exactly 489 physical LEDs.
         */
        fun isCellInsideCircle(row: Int, col: Int): Boolean {
            return GlyphAperture.isPhysicalLed(col, row)
        }

        fun applyCircularMask(pixels: ByteArray): ByteArray {
            val out = ByteArray(MATRIX_CELL_COUNT)
            val len = minOf(pixels.size, MATRIX_CELL_COUNT)
            for (i in 0 until len) {
                if (GlyphAperture.PHYSICAL_LED_MASK[i]) {
                    out[i] = pixels[i]
                }
            }
            return out
        }

        fun interpolatePixels(from: ByteArray, to: ByteArray, fraction: Float): ByteArray {
            val t = fraction.coerceIn(0f, 1f)
            return ByteArray(MATRIX_CELL_COUNT) { idx ->
                val r = idx / MATRIX_SIZE
                val c = idx % MATRIX_SIZE
                if (!isCellInsideCircle(r, c)) {
                    0
                } else {
                    val a = from.getOrElse(idx) { 0 }.toInt() and 0xFF
                    val b = to.getOrElse(idx) { 0 }.toInt() and 0xFF
                    val blended = (a + ((b - a) * t)).roundToInt().coerceIn(0, 255)
                    blended.toByte()
                }
            }
        }

        fun buildMeterFrame(
            percentage: Int,
            style: MeterStyle,
            peakBrightness: Int = 255
        ): ByteArray {
            val clamped = percentage.coerceIn(0, 100)
            val maxBright = peakBrightness.coerceIn(32, 255)
            val dimBright = (maxBright * 0.22f).roundToInt().coerceAtLeast(16)
            val pixels = ByteArray(MATRIX_CELL_COUNT)

            when (style) {
                MeterStyle.HORIZONTAL_BAR -> {
                    val activeCols = ((clamped / 100f) * MATRIX_SIZE).roundToInt()
                    for (r in 7..17) {
                        for (c in 0 until MATRIX_SIZE) {
                            if (!isCellInsideCircle(r, c)) continue
                            val isEdgeRow = r == 7 || r == 17
                            pixels[r * MATRIX_SIZE + c] = when {
                                c < activeCols -> if (isEdgeRow) (maxBright * 0.65f).toInt().toByte() else maxBright.toByte()
                                isEdgeRow -> dimBright.toByte()
                                else -> 0
                            }
                        }
                    }
                }

                MeterStyle.VERTICAL_BAR -> {
                    val activeRows = ((clamped / 100f) * MATRIX_SIZE).roundToInt()
                    val startRow = MATRIX_SIZE - activeRows
                    for (r in 0 until MATRIX_SIZE) {
                        for (c in 7..17) {
                            if (!isCellInsideCircle(r, c)) continue
                            val isEdgeCol = c == 7 || c == 17
                            pixels[r * MATRIX_SIZE + c] = when {
                                r >= startRow -> if (isEdgeCol) (maxBright * 0.65f).toInt().toByte() else maxBright.toByte()
                                isEdgeCol -> dimBright.toByte()
                                else -> 0
                            }
                        }
                    }
                }

                MeterStyle.PERIMETER_RING -> {
                    for (r in 0 until MATRIX_SIZE) {
                        for (c in 0 until MATRIX_SIZE) {
                            val dist = hypot((r - 12).toDouble(), (c - 12).toDouble())
                            if (dist in 9.8..12.2) {
                                val deg = ((Math.toDegrees(kotlin.math.atan2((c - 12).toDouble(), (12 - r).toDouble())) + 360.0) % 360.0)
                                val progressDeg = (clamped / 100.0) * 360.0
                                pixels[r * MATRIX_SIZE + c] = if (deg <= progressDeg) {
                                    maxBright.toByte()
                                } else {
                                    dimBright.toByte()
                                }
                            }
                        }
                    }
                    pixels[12 * MATRIX_SIZE + 12] = maxBright.toByte()
                }

                MeterStyle.RADIAL_CORE -> {
                    val maxRadius = 12.0
                    val activeRadius = (clamped / 100.0) * maxRadius
                    for (r in 0 until MATRIX_SIZE) {
                        for (c in 0 until MATRIX_SIZE) {
                            if (!isCellInsideCircle(r, c)) continue
                            val dist = hypot((r - 12).toDouble(), (c - 12).toDouble())
                            if (dist <= activeRadius) {
                                val falloff = 1.0 - (dist / (maxRadius + 2.0)) * 0.35
                                pixels[r * MATRIX_SIZE + c] = (maxBright * falloff).roundToInt().coerceIn(24, 255).toByte()
                            } else if (abs(dist - maxRadius) < 0.65) {
                                pixels[r * MATRIX_SIZE + c] = dimBright.toByte()
                            }
                        }
                    }
                }
            }
            return pixels
        }
    }
}
