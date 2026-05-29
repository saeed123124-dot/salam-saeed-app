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
            textSize = 16.5f
            setTextColor(Color.DKGRAY)
            text = "در حال دریافت اطلاعات شبکه..."
        }

        mainLayout.addView(title)
        mainLayout.addView(infoText)
        setContentView(mainLayout)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        val permissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (permissions.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, 102)
        } else {
            startUpdating()
        }
    }

    private fun startUpdating() {
        handler.post(object : Runnable {
            override fun run() {
                updateDetailedNetworkInfo()
                handler.postDelayed(this, 2500)
            }
        })
    }

    private fun updateDetailedNetworkInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            infoText.text = "⚠️ مجوز READ_PHONE_STATE داده نشده"
            return
        }

        val sb = StringBuilder()

        val subscriptionManager = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val subs = subscriptionManager.activeSubscriptionInfoList ?: emptyList()

        if (subs.isEmpty()) {
            sb.append("هیچ سیم‌کارت فعالی پیدا نشد\n")
        } else {
            subs.forEach { subInfo ->
                val slot = subInfo.simSlotIndex + 1
                val subId = subInfo.subscriptionId
                val tm = telephonyManager?.createForSubscriptionId(subId)

                sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n")
                sb.append("🔴 سیم‌کارت $slot - ${subInfo.displayName}\n")
                sb.append("📱 اپراتور: ${tm?.networkOperatorName ?: "نامشخص"}\n")

                val operator = tm?.networkOperator
                sb.append("🌍 MCC: ${operator?.take(3) ?: "-"} | MNC: ${operator?.drop(3) ?: "-"}\n")
                sb.append("🌐 نوع شبکه: ${getNetworkType(tm)}\n")

                // اطلاعات سلول
                val cellInfoStr = getCellInfoStr(tm)
                sb.append(cellInfoStr)
                sb.append("\n")
            }
        }

        infoText.text = sb.toString()

        // تشخیص جمر / سیگنال ضعیف
        if (isSignalVeryWeak()) {
            mainLayout.setBackgroundColor(Color.RED)
            infoText.setTextColor(Color.WHITE)
        } else {
            mainLayout.setBackgroundColor(Color.WHITE)
            infoText.setTextColor(Color.DKGRAY)
        }
    }

    private fun getNetworkType(tm: TelephonyManager?): String {
        return when (tm?.networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G+"
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> "نامشخص"
        }
    }

    private fun getCellInfoStr(tm: TelephonyManager?): String {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "   (نیاز به مجوز لوکیشن برای Cell ID)\n"
        }

        val cellInfos = tm?.allCellInfo ?: return "   اطلاعات سلول موجود نیست\n"

        val sb = StringBuilder()
        for (cell in cellInfos) {
            when (cell) {
                is CellInfoLte -> {
                    val id = cell.cellIdentity as CellIdentityLte
                    sb.append("   📍 Cell ID: ${id.cid}\n")
                    sb.append("   🏢 TAC: ${id.tac}\n")
                    sb.append("   📡 PCI: ${id.pci}\n")
                    sb.append("   📶 Signal: ${cell.cellSignalStrength.dbm} dBm\n")
                }
                is CellInfoNr -> {
                    val id = cell.cellIdentity as CellIdentityNr
                    sb.append("   📍 5G Cell ID: ${id.nci}\n")
                    sb.append("   📡 PCI: ${id.pci}\n")
                    sb.append("   📶 Signal: ${cell.cellSignalStrength.dbm} dBm\n")
                }
            }
        }
        return sb.toString()
    }

    private fun isSignalVeryWeak(): Boolean {
        val subs = (getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager).activeSubscriptionInfoList ?: return false
        for (sub in subs) {
            val tm = telephonyManager?.createForSubscriptionId(sub.subscriptionId)
            if (tm?.signalStrength?.level ?: 0 <= 1) {
                return true
            }
        }
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startUpdating()
        }
    }
}
