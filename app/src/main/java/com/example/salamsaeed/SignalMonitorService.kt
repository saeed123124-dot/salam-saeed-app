package com.example.salamsaeed

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class SignalMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "signal_monitor_channel"
        const val NOTIFICATION_ID = 101
        var isRunning = false
    }

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var notificationManager: NotificationManager
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var outOfServiceCheckRunnable: Runnable? = null

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onServiceStateChanged(serviceState: ServiceState) {
            checkSignalCondition()
        }

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            checkSignalCondition()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_SERVICE_STATE or
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        } else {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        stopAlarm()
        cancelOutOfServiceCheck()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkSignalCondition() {
        val serviceState = telephonyManager.serviceState   // ServiceState?
        val signalDbm = getSignalStrengthDbm()

        // تصحیح: استفاده از ?.state برای دریافت وضعیت به صورت Int
        val currentState = serviceState?.state
        val isOutOfService = currentState == ServiceState.STATE_OUT_OF_SERVICE
        val isPowerOff = currentState == ServiceState.STATE_POWER_OFF
        val signalLost = (signalDbm <= -120 || signalDbm == -2300)

        if (isOutOfService || isPowerOff || signalLost) {
            if (outOfServiceCheckRunnable == null) {
                outOfServiceCheckRunnable = Runnable {
                    val freshState = telephonyManager.serviceState
                    val freshDbm = getSignalStrengthDbm()
                    // تصحیح: مقایسه با freshState?.state
                    if (freshState?.state == ServiceState.STATE_OUT_OF_SERVICE ||
                        freshState?.state == ServiceState.STATE_POWER_OFF ||
                        freshDbm <= -120 || freshDbm == -2300) {
                        triggerAlarm()
                    }
                    outOfServiceCheckRunnable = null
                }
                handler.postDelayed(outOfServiceCheckRunnable!!, 3000)
            }
        } else {
            cancelOutOfServiceCheck()
            stopAlarm()
        }
    }

    private fun getSignalStrengthDbm(): Int {
        val ss = telephonyManager.signalStrength ?: return -2300
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cellSignals = ss.cellSignalStrengths
            if (cellSignals.isNotEmpty()) cellSignals[0].dbm else -2300
        } else {
            try {
                val method = SignalStrength::class.java.getDeclaredMethod("getDbm")
                method.isAccessible = true
                method.invoke(ss) as Int
            } catch (e: Exception) {
                -2300
            }
        }
    }

    private fun cancelOutOfServiceCheck() {
        outOfServiceCheckRunnable?.let {
            handler.removeCallbacks(it)
            outOfServiceCheckRunnable = null
        }
    }

    private fun triggerAlarm() {
        if (mediaPlayer?.isPlaying == true) return

        val alarmIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("هشدار قطع سیگنال")
            .setContentText("آماده باشید 123")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        notificationManager.notify(999, notification)

        try {
            val alarmUri: Uri = Settings.System.DEFAULT_ALARM_ALERT_URI
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@SignalMonitorService, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("SignalService", "خطا در پخش صدا", e)
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        notificationManager.cancel(999)
    }

    private fun buildForegroundNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_manage)
        .setContentTitle("پایش سیگنال")
        .setContentText("نظارت بر سیگنال تلفن در پس‌زمینه فعال است")
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "هشدارهای پایش سیگنال",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "اعلان‌های مربوط به قطع یا غیرعادی شدن سیگنال"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
