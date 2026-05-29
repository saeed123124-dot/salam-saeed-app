package com.example.salamsaeed

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.view.Gravity
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
            setPadding(30, 60, 30, 60)
        }

        val title = TextView(this).apply {
            text = "سلام سعید"
            textSize = 32f
            setTextColor(Color.BLACK)
        }

        infoText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.DKGRAY)
            text = "در حال دریافت اطلاعات..."
        }

        mainLayout.addView(title)
        mainLayout.addView(infoText)
        setContentView(mainLayout)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        val permissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (permissions.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, 103)
        } else {
            startUpdating()
        }
    }

    private fun startUpdating() {
        handler.post(object : Runnable {
            override fun run() {
                updateNetworkInfo()
                handler.postDelayed(this, 3000)
            }
        })
    }

    private fun updateNetworkInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            infoText.text = "⚠️ مجوزهای لازم داده نشده است"
            return
        }

        val sb = StringBuilder()

        // اطلاعات اصلی
        sb.append("📱 اپراتور: ${telephonyManager?.networkOperatorName ?: "نامشخص"}\n")
        sb.append("🌐 نوع شبکه: ${getNetworkType()}\n")

        val operator = telephonyManager?.networkOperator
        sb.append("🌍 MCC: ${operator?.take(3) ?: "-"} | MNC: ${operator?.drop(3) ?: "-"}\n\n")

        // اطلاعات سیم‌کارت‌ها
        try {
            val subscriptionManager = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subs = subscriptionManager.activeSubscriptionInfoList

            subs?.forEach { sub ->
                val slot = sub.simSlotIndex + 1
                sb.append("🔴 سیم‌کارت $slot: ${sub.displayName}\n")
            }
        } catch (e: Exception) {
            sb.append("اطلاعات سیم‌کارت در دسترس نیست\n")
        }

        // اطلاعات سلول
        sb.append("\n📍 اطلاعات BTS:\n")
        val cellInfoStr = getCellInfo()
        sb.append(cellInfoStr)

        infoText.text = sb.toString()

        // تشخیص جمر
        if (isSignalWeak()) {
            mainLayout.setBackgroundColor(Color.RED)
            infoText.setTextColor(Color.WHITE)
        } else {
            mainLayout.setBackgroundColor(Color.WHITE)
            infoText.setTextColor(Color.DKGRAY)
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
            else -> "نامشخص"
        }
    }

    private fun getCellInfo(): String {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "   نیاز به مجوز لوکیشن\n"
        }

        val cellInfos = telephonyManager?.allCellInfo ?: return "   اطلاعات سلول موجود نیست\n"

        val sb = StringBuilder()
        for (cell in cellInfos) {
            when (cell) {
                is CellInfoLte -> {
                    val id = cell.cellIdentity as CellIdentityLte
                    sb.append("   Cell ID: ${id.cid}\n")
                    sb.append("   TAC: ${id.tac}\n")
                    sb.append("   Signal: ${cell.cellSignalStrength.dbm} dBm\n\n")
                }
                is CellInfoNr -> {
                    val id = cell.cellIdentity as CellIdentityNr
                    sb.append("   5G Cell ID: ${id.nci}\n")
                    sb.append("   Signal: ${cell.cellSignalStrength.dbm} dBm\n\n")
                }
            }
        }
        return if (sb.isEmpty()) "   اطلاعات سلول یافت نشد\n" else sb.toString()
    }

    private fun isSignalWeak(): Boolean {
        val signal = telephonyManager?.signalStrength?.level ?: 0
        return signal <= 1
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 103 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startUpdating()
        }
    }
}
