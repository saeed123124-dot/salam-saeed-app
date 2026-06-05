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
        var isRunning = false   // وضعیت اجرای سرویس برای استفاده در Activity
    }

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var notificationManager: NotificationManager
    private var mediaPlayer: MediaPlayer? = null

    // Handler برای مدیریت تأخیر در تشخیص قطعی سیگنال
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
        // شروع سرویس به صورت پیش‌زمینه با اعلان
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        // ثبت شنونده وضعیت شبکه و قدرت سیگنال
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_SERVICE_STATE or
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        } else {
            stopSelf() // اگر مجوز تلفن نباشد سرویس بسته شود
        }

        return START_STICKY  // سعی کند دوباره راه‌اندازی شود اگر کشته شد
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        stopAlarm()
        cancelOutOfServiceCheck()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------------
    // بررسی وضعیت و تصمیم‌گیری برای هشدار
    // ------------------------------------------------------------------------
    private fun checkSignalCondition() {
        val serviceState = telephonyManager.serviceState
        val signalDbm = getSignalStrengthDbm()

        val isOutOfService = serviceState == ServiceState.STATE_OUT_OF_SERVICE
        val isPowerOff = serviceState == ServiceState.STATE_POWER_OFF
        val signalLost = (signalDbm <= -120 || signalDbm == -2300)  // -2300 یعنی نامعتبر

        // اگر خط قطع باشد یا تلفن خاموش باشد یا سیگنال عملاً صفر
        if (isOutOfService || isPowerOff || signalLost) {
            // با یک تأخیر کوتاه (۳ ثانیه) بررسی می‌کنیم که آیا وضعیت همچنان مشکل‌دار است یا موقت بوده
            if (outOfServiceCheckRunnable == null) {
                outOfServiceCheckRunnable = Runnable {
                    // دوباره وضعیت را چک کن
                    val freshState = telephonyManager.serviceState
                    val freshDbm = getSignalStrengthDbm()
                    if (freshState == ServiceState.STATE_OUT_OF_SERVICE ||
                        freshState == ServiceState.STATE_POWER_OFF ||
                        freshDbm <= -120 || freshDbm == -2300) {
                        triggerAlarm()
                    }
                    outOfServiceCheckRunnable = null
                }
                handler.postDelayed(outOfServiceCheckRunnable!!, 3000)
            }
        } else {
            // اگر وضعیت عادی شد، کار تأخیری را لغو و آلارم را قطع کن
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

    // ------------------------------------------------------------------------
    // پخش آلارم صوتی و نمایش اعلان هشدار
    // ------------------------------------------------------------------------
    private fun triggerAlarm() {
        // فقط اگر آلارم از قبل فعال نیست
        if (mediaPlayer?.isPlaying == true) return

        // ۱. نمایش اعلان با متن "آماده باشید 123"
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
            .setOngoing(true)    // تا زمانی که کاربر اقدامی نکند باقی بماند
            .build()

        notificationManager.notify(999, notification)

        // ۲. پخش صدای آلارم (حلقه‌ای)
        try {
            // استفاده از صدای پیش‌فرض آلارم سیستم
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
        // توقف و رهاسازی MediaPlayer
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null

        // حذف اعلان هشدار
        notificationManager.cancel(999)
    }

    // ------------------------------------------------------------------------
    // اعلان پیش‌زمینه سرویس (در نوار وضعیت)
    // ------------------------------------------------------------------------
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
