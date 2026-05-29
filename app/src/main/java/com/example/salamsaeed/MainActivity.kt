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
                handler.postDelayed(this, 2000) // هر ۲ ثانیه
            }
        })
    }

    private fun updateDetailedNetworkInfo() {
        if (!checkPermissions()) {
            infoText.text = "⚠️ مجوزهای لازم داده نشده است"
            return
        }

        val sb = StringBuilder()

        // اطلاعات هر دو سیم‌کارت
        val subscriptionManager = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList

        if (activeSubscriptions.isNullOrEmpty()) {
            sb.append("هیچ سیم‌کارت فعالی یافت نشد\n")
        } else {
            activeSubscriptions.forEach { sub ->
                val tm = telephonyManager?.createForSubscriptionId(sub.subscriptionId)
                val slot = sub.simSlotIndex + 1

                sb.append("━━━━━━━━━━━━━━━━━━\n")
                sb.append("🔴 سیم‌کارت $slot (${sub.displayName})\n")
                sb.append("📱 اپراتور: ${tm?.networkOperatorName ?: "نامشخص"}\n")

                // MCC + MNC
                val mcc = tm?.networkOperator?.take(3) ?: "—"
                val mnc = tm?.networkOperator?.drop(3) ?: "—"
                sb.append("🌍 MCC: $mcc | MNC: $mnc\n")

                // نوع شبکه
                sb.append("🌐 شبکه: ${getNetworkType(tm)}\n")

                // اطلاعات سلول (BTS)
                val cellInfo = getCellInfo(tm)
                sb.append(cellInfo)

                sb.append("\n")
            }
        }

        infoText.text = sb.toString()

        // تشخیص جمر / قطع سیگنال
        val isJammer = isSignalVeryWeak()
        if (isJammer) {
            mainLayout.setBackgroundColor(Color.RED)
            infoText.setTextColor(Color.WHITE)
        } else {
            mainLayout.setBackgroundColor(Color.WHITE)
            infoText.setTextColor(Color.DKGRAY)
        }
    }

    private fun getNetworkType(tm: TelephonyManager?): String {
        return when (tm?.networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G+ (HSPA+)"
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
            else -> "نامشخص"
        }
    }

    private fun getCellInfo(tm: TelephonyManager?): String {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "   سلول: نیاز به مجوز لوکیشن\n"
        }

        val cellInfos = tm?.allCellInfo ?: return "   اطلاعات سلول در دسترس نیست\n"

        val sb = StringBuilder()
        cellInfos.forEach { cell ->
            when (cell) {
                is CellInfoLte -> {
                    val identity = cell.cellIdentity as CellIdentityLte
                    sb.append("   📍 Cell ID: ${identity.cid}\n")
                    sb.append("   🏢 TAC/LAC: ${identity.tac}\n")
                    sb.append("   📡 PCI: ${identity.pci}\n")
                    sb.append("   📶 قدرت: ${cell.cellSignalStrength.dbm} dBm\n")
                }
                is CellInfoNr -> {
                    val identity = cell.cellIdentity as CellIdentityNr
                    sb.append("   📍 5G Cell ID: ${identity.nci}\n")
                    sb.append("   📡 PCI: ${identity.pci}\n")
                    sb.append("   📶 قدرت: ${cell.cellSignalStrength.dbm} dBm\n")
                }
                // می‌توانی gsm و wcdma هم اضافه کنی اگر لازم شد
            }
        }
        return sb.toString()
    }

    private fun isSignalVeryWeak(): Boolean {
        val subscriptions = (getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager).activeSubscriptionInfoList
        subscriptions?.forEach { sub ->
            val tm = telephonyManager?.createForSubscriptionId(sub.subscriptionId)
            val strength = tm?.signalStrength?.getLevel() ?: 0
            if (strength <= 1) return true
        }
        return false
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startUpdating()
        }
    }
}
