package com.example.salamsaeed

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var infoText: TextView
    private lateinit var mainLayout: LinearLayout
    private var telephonyManager: TelephonyManager? = null
    private var titleView: TextView? = null  // نام متغیر را عوض کردیم تا با متغیر محلی اشتباه نشود

    // گوش‌دهنده تغییرات سیگنال
    private val phoneStateListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            updateNetworkInfo()
        }
    }

    // درخواست مجوز به روش جدید
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.READ_PHONE_STATE] == true) {
                startMonitoringSignal()
            } else {
                infoText.text = "⚠️ مجوز READ_PHONE_STATE الزامی است. برنامه بسته خواهد شد."
                // می‌توانی یک finish() تأخیردار بگذاری
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 80, 40, 80)
        }

        // عنوان را می‌سازیم و در پراپرتی کلاس ذخیره می‌کنیم
        val title = TextView(this).apply {
            text = "سلام سعید"
            textSize = 34f
            setTextColor(Color.BLACK)
        }
        titleView = title  // این خط حیاتی قبلاً جا افتاده بود

        infoText = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.DKGRAY)
            text = "در حال دریافت اطلاعات شبکه..."
        }

        mainLayout.addView(title)
        mainLayout.addView(infoText)
        setContentView(mainLayout)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        // بررسی مجوز
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED -> startMonitoringSignal()
            else -> requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
        }
    }

    private fun startMonitoringSignal() {
        // استفاده از PhoneStateListener برای کاهش مصرف باتری
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        // یک بار هم بلافاصله بروز کن
        updateNetworkInfo()
    }

    private fun updateNetworkInfo() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            infoText.text = "⚠️ مجوز READ_PHONE_STATE داده نشده است"
            return
        }

        val sb = StringBuilder()

        val operatorName = telephonyManager?.networkOperatorName ?: "نامشخص"
        val simState = when (telephonyManager?.simState) {
            TelephonyManager.SIM_STATE_READY -> "آماده"
            TelephonyManager.SIM_STATE_ABSENT -> "سیم‌کارت وجود ندارد"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "نیاز به PIN"
            else -> "نامشخص"
        }
        val networkType = getNetworkType()

        sb.append("📱 اپراتور: $operatorName\n")
        sb.append("🔋 وضعیت سیم‌کارت: $simState\n")
        sb.append("🌐 نوع شبکه: $networkType\n")

        val signalDbm = getSignalStrengthDbm()
        sb.append("📶 قدرت سیگنال: $signalDbm dBm\n")

        // اطلاعات اضافی پیشنهادی (اختیاری)
        val dataState = getDataState()
        sb.append("📡 وضعیت داده: $dataState\n")
        val roaming = if (telephonyManager?.isNetworkRoaming == true) "خارج از شبکه" else "شبکه خانگی"
        sb.append("🗺 رومینگ: $roaming\n")

        infoText.text = sb.toString()

        // تغییر رنگ بر اساس قدرت سیگنال
        if (signalDbm < -110) {
            mainLayout.setBackgroundColor(Color.RED)
            infoText.setTextColor(Color.WHITE)
            titleView?.setTextColor(Color.WHITE)  // حالا چون titleView مقدار دارد کار می‌کند
        } else if (signalDbm in -100..-90) {
            mainLayout.setBackgroundColor(Color.YELLOW)
            infoText.setTextColor(Color.BLACK)
            titleView?.setTextColor(Color.BLACK)
        } else {
            mainLayout.setBackgroundColor(Color.WHITE)
            infoText.setTextColor(Color.DKGRAY)
            titleView?.setTextColor(Color.BLACK)
        }
    }

    private fun getNetworkType(): String {
        return when (telephonyManager?.networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA -> "3G+"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> "نامشخص / وای‌فای"
        }
    }

    private fun getSignalStrengthDbm(): Int {
        val signalStrength = telephonyManager?.signalStrength ?: return -999
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // روش استاندارد API 29+
            val cellSignals = signalStrength.cellSignalStrengths
            if (cellSignals.isNotEmpty()) {
                cellSignals[0].dbm   // مقدار دقیق dBm
            } else {
                -110
            }
        } else {
            // روش قدیمی با انعکاس (بهبودیافته)
            try {
                val field = SignalStrength::class.java.getDeclaredMethod("getDbm")
                field.isAccessible = true
                field.invoke(signalStrength) as Int
            } catch (e: Exception) {
                -110  // مقدار پیش‌فرض
            }
        }
    }

    private fun getDataState(): String {
        return when (telephonyManager?.dataState) {
            TelephonyManager.DATA_CONNECTED -> "متصل"
            TelephonyManager.DATA_SUSPENDED -> "معلق"
            TelephonyManager.DATA_DISCONNECTED -> "قطع"
            else -> "نامشخص"
        }
    }

    override fun onDestroy() {
        // حذف گوش‌دهنده هنگام بستن
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        super.onDestroy()
    }
}
