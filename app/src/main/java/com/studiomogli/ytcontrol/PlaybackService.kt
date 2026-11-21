package com.studiomogli.ytcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class PlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "ytcontrol_playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_UPDATE_STATE = "com.studiomogli.ytcontrol.ACTION_UPDATE_STATE"
        const val ACTION_PLAY_PAUSE = "com.studiomogli.ytcontrol.ACTION_PLAY_PAUSE"
        const val ACTION_STOP = "com.studiomogli.ytcontrol.ACTION_STOP"

        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"

        private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var wakeLock: PowerManager.WakeLock? = null

    private var isPlaying: Boolean = false
    private var currentTitle: String = "YT Control"
    private var currentArtist: String = "Studio Mogli"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initWakeLock()
        initMediaSession()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "YT Control Background Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps YT Control audio active when the screen is off — Made by Studio Mogli"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun initWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "YTControl::AudioWakeLock"
        ).apply { setReferenceCounted(false) }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "YTControlSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { WebViewBridge.sendControl("play") }
                override fun onPause() { WebViewBridge.sendControl("pause") }
                override fun onStop() { stopServiceAndExit() }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                WebViewBridge.sendControl("toggle")
                updateForegroundNotification()
            }
            ACTION_STOP -> {
                stopServiceAndExit()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATE -> {
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: currentArtist
                syncWakeLock()
                updateMediaSessionState()
                updateForegroundNotification()
            }
            else -> {
                updateMediaSessionState()
                updateForegroundNotification()
            }
        }
        return START_STICKY
    }

    private fun syncWakeLock() {
        val lock = wakeLock ?: return
        if (isPlaying) { if (!lock.isHeld) lock.acquire(WAKE_LOCK_TIMEOUT_MS) }
        else { if (lock.isHeld) lock.release() }
    }

    private fun updateMediaSessionState() {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .build()
        mediaSession.setMetadata(metadata)

        val stateCode = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP
            )
            .setState(stateCode, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun updateForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePendingIntent = buildServiceActionIntent(ACTION_PLAY_PAUSE, 2)
        val stopPendingIntent = buildServiceActionIntent(ACTION_STOP, 4)

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseIcon, playPauseLabel, playPausePendingIntent)
            .addAction(R.drawable.ic_close, "Exit", stopPendingIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }

    private fun buildServiceActionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopServiceAndExit() {
        releaseResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        WebViewBridge.requestExit()
    }

    private fun releaseResources() {
        wakeLock?.let { if (it.isHeld) it.release() }
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
    }

    override fun onDestroy() {
        releaseResources()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
