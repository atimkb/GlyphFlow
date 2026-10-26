package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.domain.model.ConditionType
import com.example.domain.model.MediaTriggerState
import com.example.domain.model.SystemSignalEvent
import com.example.engine.EventIngestBus

/**
 * Concrete [NotificationListenerService] intercepting:
 * 1) Active system notifications (`onNotificationPosted`) with deep extraction of sender/conversation
 *    titles (`EXTRA_CONVERSATION_TITLE`, `EXTRA_TITLE`) and message body (`EXTRA_BIG_TEXT`, `EXTRA_TEXT`,
 *    `EXTRA_SUB_TEXT`) for Essential Glyph rules, plus notification dismissals (`onNotificationRemoved`)
 *    so persistent Essential Glyphs automatically clear when dismissed.
 * 2) Active `MediaSessionManager` controllers (`PlaybackState.STATE_PLAYING`, `STATE_PAUSED`, `STATE_STOPPED`,
 *    `STATE_NONE`) so pausing or stopping audio immediately flushes and shuts down the Glyph Matrix.
 */
class GlyphNotificationListenerService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private val trackedControllers = mutableMapOf<String, Pair<MediaController, MediaController.Callback>>()

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        bindMediaControllers(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        EventIngestBus.setNotificationListenerConnected(true)
        setupMediaSessionMonitoring()
    }

    override fun onListenerDisconnected() {
        teardownMediaSessionMonitoring()
        EventIngestBus.setNotificationListenerConnected(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == applicationContext.packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val rawTitle = (extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG))?.toString().orEmpty()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty()
        val senderTitle = when {
            conversationTitle.isNotBlank() && rawTitle.isNotBlank() && !rawTitle.contains(conversationTitle, ignoreCase = true) ->
                "$conversationTitle: $rawTitle"
            rawTitle.isNotBlank() -> rawTitle
            else -> conversationTitle
        }

        val content = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_INFO_TEXT))?.toString().orEmpty()
        val subtext = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        val hasProgress = extras.containsKey(Notification.EXTRA_PROGRESS) &&
            extras.containsKey(Notification.EXTRA_PROGRESS_MAX)
        val rawProgress = if (hasProgress) extras.getInt(Notification.EXTRA_PROGRESS, 0) else null
        val rawProgressMax = if (hasProgress) extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) else null
        val validProgress = if (rawProgressMax != null && rawProgressMax > 0) rawProgress else null
        val validProgressMax = if (rawProgressMax != null && rawProgressMax > 0) rawProgressMax else null
        val computedPct = if (validProgress != null && validProgressMax != null && validProgressMax > 0) {
            ((validProgress.toFloat() / validProgressMax.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else null

        val appLabel = resolveAppLabel(pkg)

        val payloadAuditEntry = com.example.domain.model.NotificationPayloadAuditEntry(
            packageName = pkg,
            appLabel = appLabel,
            rawExtraTitle = senderTitle,
            rawExtraText = content,
            rawExtraSubText = subtext,
            rawExtraProgress = validProgress,
            rawExtraProgressMax = validProgressMax,
            computedProgressPercent = computedPct
        )

        EventIngestBus.recordPayloadInspectorEvent(payloadAuditEntry)

        if (EventIngestBus.isNotificationSnifferActive.value) {
            EventIngestBus.captureSnifferNotification(payloadAuditEntry)
        }

        val signal = SystemSignalEvent(
            conditionType = ConditionType.NOTIFICATION,
            sourcePackage = pkg,
            sourceAppLabel = appLabel,
            title = senderTitle,
            content = content,
            subtext = subtext,
            progress = validProgress,
            progressMax = validProgressMax,
            isSimulated = false
        )

        EventIngestBus.emitSignal(signal)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val pkg = sbn?.packageName ?: return
        if (pkg == applicationContext.packageName) return
        // Actively clear the matrix if the dismissed notification's package was driving an active rule
        EventIngestBus.emitClearForSource(pkg)
    }

    private fun setupMediaSessionMonitoring() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            mediaSessionManager = msm
            val componentName = ComponentName(this, GlyphNotificationListenerService::class.java)
            val initialControllers = msm.getActiveSessions(componentName)
            bindMediaControllers(initialControllers)
            msm.addOnActiveSessionsChangedListener(activeSessionsListener, componentName)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun teardownMediaSessionMonitoring() {
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        } catch (_: Exception) {
        }
        trackedControllers.values.forEach { (controller, cb) ->
            runCatching { controller.unregisterCallback(cb) }
        }
        trackedControllers.clear()
    }

    private fun bindMediaControllers(controllers: List<MediaController>) {
        trackedControllers.values.forEach { (controller, cb) ->
            runCatching { controller.unregisterCallback(cb) }
        }
        trackedControllers.clear()

        if (controllers.isEmpty()) {
            EventIngestBus.emitClearForSource("MEDIA_STOPPED")
            return
        }

        for (controller in controllers) {
            val pkg = controller.packageName
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    val pbState = state?.state ?: PlaybackState.STATE_NONE
                    when (pbState) {
                        PlaybackState.STATE_PLAYING -> {
                            val title = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                                ?: resolveAppLabel(pkg)
                            EventIngestBus.emitSignal(
                                SystemSignalEvent(
                                    conditionType = ConditionType.MEDIA_PLAYBACK,
                                    sourcePackage = pkg,
                                    sourceAppLabel = resolveAppLabel(pkg),
                                    title = title,
                                    mediaState = MediaTriggerState.PLAYING
                                )
                            )
                        }
                        PlaybackState.STATE_PAUSED,
                        PlaybackState.STATE_STOPPED,
                        PlaybackState.STATE_NONE -> {
                            EventIngestBus.emitClearForSource("MEDIA_STOPPED")
                            EventIngestBus.emitSignal(
                                SystemSignalEvent(
                                    conditionType = ConditionType.MEDIA_PLAYBACK,
                                    sourcePackage = pkg,
                                    sourceAppLabel = resolveAppLabel(pkg),
                                    title = "Playback Stopped",
                                    mediaState = MediaTriggerState.PAUSED
                                )
                            )
                        }
                    }
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
                    if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                        EventIngestBus.emitSignal(
                            SystemSignalEvent(
                                conditionType = ConditionType.MEDIA_PLAYBACK,
                                sourcePackage = pkg,
                                sourceAppLabel = resolveAppLabel(pkg),
                                title = title,
                                mediaState = MediaTriggerState.TRACK_CHANGED
                            )
                        )
                    }
                }

                override fun onSessionDestroyed() {
                    EventIngestBus.emitClearForSource("MEDIA_STOPPED")
                }
            }
            runCatching {
                controller.registerCallback(callback)
                trackedControllers[pkg] = controller to callback
            }
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.')
        }
    }
}
