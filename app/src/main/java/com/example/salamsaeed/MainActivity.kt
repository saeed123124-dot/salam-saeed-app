package com.example.salamsaeed

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.*
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
            setPadding(40, 60, 40, 60)
        }

        val title = TextView(this).apply {
            text = "سلام سعید"
            textSize = 34f
            setTextColor(Color.BLACK)
        }

        infoText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.DKGRAY)
            text = "در حال دریافت اطلاعات شبکه..."
        }

        mainLayout.addView(title)
        mainLayout.addView(infoText)
        setContentView(mainLayout)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        // درخواست مجوز
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_COARSE_LOCATION), 101)
        } else {
            startUpdatingNetworkInfo()
        }
    }

    private fun startUpdatingNetworkInfo() {
        handler.post(object : Runnable {
            override fun run() {
                updateNetworkInfo()
                handler.postDelayed(this, 3000) // هر ۳ ثانیه بروزرسانی
            }
        })
    }

    private fun updateNetworkInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            infoText.text = "مجوزهای لازم داده نشده است"
            return
        }

        val sb = StringBuilder()

        // اطلاعات سیم‌کارت و اپراتور
        val operatorName = telephonyManager?.networkOperatorName ?: "نامشخص"
        val simState = when (telephonyManager?.simState) {
            TelephonyManager.SIM_STATE_READY -> "آماده"
            TelephonyManager.SIM_STATE_ABSENT -> "سیم‌کارت وجود ندارد"
            else -> "نامشخص"
        }

        sb.append("📱 اپراتور: $operatorName\n")
        sb.append("🔋 وضعیت سیم‌کارت: $simState\n")

        // نوع شبکه
        val networkType = getNetworkType()
        sb.append("🌐 نوع شبکه: $networkType\n")

        // قدرت سیگنال
        val signalStrength = getSignalStrength()
        sb.append("📶 قدرت سیگنال: $signalStrength dBm\n\n")

        infoText.text = sb.toString()

        // تشخیص قطع سیگنال یا جمر (سیگنال خیلی ضعیف)
        if (signalStrength < -110) {
            mainLayout.setBackgroundColor(Color.RED)
            infoText.setTextColor(Color.WHITE)
        } else {
            mainLayout.setBackgroundColor(Color.WHITE)
            infoText.setTextColor(Color.DKGRAY)
        }
    }

    private fun getNetworkType(): String {
        return when (telephonyManager?.networkType) {
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP -> "3G+"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> "نامشخص"
        }
    }

    private fun getSignalStrength(): Int {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return -999
        }

        val signal = telephonyManager?.signalStrength
        return signal?.getLevel() ?: -999 // تقریبی
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startUpdatingNetworkInfo()
        }
    }
}
