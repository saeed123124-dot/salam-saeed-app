package com.example.salamsaeed

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var infoText: TextView
    private lateinit var mainLayout: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var telephonyManager: TelephonyManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 80, 40, 80)
        }

        val title = TextView(this).apply {
            text = "سلام سعید"
            textSize = 34f
            setTextColor(Color.BLACK)
        }

        infoText = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.DKGRAY)
            text = "در حال دریافت اطلاعات شبکه..."
        }

        mainLayout.addView(title)
        mainLayout.addView(infoText)
        setContentView(mainLayout)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_COARSE_LOCATION), 101)
        } else {
            startUpdatingNetworkInfo()
        }
    }

    private fun startUpdatingNetworkInfo() {
        handler.post(object : Runnable {
            override fun run() {
                updateNetworkInfo()
                handler.postDelayed(this, 2500) // هر ۲.۵ ثانیه
            }
        })
    }

    private fun updateNetworkInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
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

        infoText.text = sb.toString()

        // تشخیص جمر یا قطع سیگنال (زیر -110 dBm قرمز شود)
        if (signalDbm < -110) {
            mainLayout.setBackgroundColor(Color.RED)
            infoText.setTextColor(Color.WHITE)
            title?.setTextColor(Color.WHITE) // عنوان هم سفید شود
        } else {
            mainLayout.setBackgroundColor(Color.WHITE)
            infoText.setTextColor(Color.DKGRAY)
            title?.setTextColor(Color.BLACK)
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
        return try {
            // روش جدیدتر برای دریافت dBm
            val field = signalStrength.javaClass.getDeclaredField("mLevel")
            field.isAccessible = true
            val level = field.getInt(signalStrength)
            when (level) {
                4 -> -85
                3 -> -95
                2 -> -105
                1 -> -115
                else -> -120
            }
        } catch (e: Exception) {
            -110 // مقدار پیش‌فرض
        }
    }

    // متغیر title رو تعریف کنیم
    private var title: TextView? = null

    // در onCreate بعد از ساخت title اضافه کن:
    // title = TextView(...)   ← این خط رو داخل onCreate بعد از ساخت title اضافه کن
}
