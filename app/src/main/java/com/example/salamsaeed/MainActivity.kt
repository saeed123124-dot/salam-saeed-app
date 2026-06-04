package com.example.salamsaeed

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.telephony.*
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mainLayout: LinearLayout
    private lateinit var generalInfoText: TextView
    private lateinit var towerInfoText: TextView
    private var titleView: TextView? = null
    private var telephonyManager: TelephonyManager? = null

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            updateAllInfo()
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val phoneGranted = permissions[Manifest.permission.READ_PHONE_STATE] ?: false
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

            if (phoneGranted && locationGranted) {
                startMonitoring()
            } else {
                generalInfoText.text = "⚠️ برای نمایش کامل اطلاعات دکل‌ها، هر دو مجوز لازم است."
                if (phoneGranted) {
                    startMonitoring()
                } else {
                    finishAffinity()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
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
        titleView = title

        generalInfoText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.DKGRAY)
            text = "در حال دریافت اطلاعات..."
        }

        towerInfoText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.DKGRAY)
            text = "لیست دکل‌ها: ..."
            setPadding(0, 30, 0, 0)
        }

        mainLayout.addView(title)
        mainLayout.addView(generalInfoText)
        mainLayout.addView(towerInfoText)

        scrollView.addView(mainLayout)
        setContentView(scrollView)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        checkPermissions()
    }

    private fun checkPermissions() {
        val phonePerm = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
        val locPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        val needed = mutableListOf<String>()
        if (phonePerm != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_PHONE_STATE)
        if (locPerm != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (needed.isEmpty()) {
            startMonitoring()
        } else {
            requestPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startMonitoring() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        }
        updateAllInfo()
    }

    @SuppressLint("MissingPermission")
    private fun updateAllInfo() {
        val now = dateFormat.format(Date())

        val sbGeneral = StringBuilder()

        val operatorName = telephonyManager?.networkOperatorName ?: "نامشخص"
        val networkOperator = telephonyManager?.networkOperator ?: ""
        val mcc = if (networkOperator.length >= 3) networkOperator.substring(0, 3) else "نامشخص"
        val mnc = if (networkOperator.length >= 5) networkOperator.substring(3) else "نامشخص"

        val simState = when (telephonyManager?.simState) {
            TelephonyManager.SIM_STATE_READY -> "آماده"
            TelephonyManager.SIM_STATE_ABSENT -> "سیم‌کارت وجود ندارد"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "نیاز به PIN"
            else -> "نامشخص"
        }

        val dataNetworkType = getDataNetworkTypeName()
        val voiceNetworkType = getVoiceNetworkTypeName()
        val dataState = when (telephonyManager?.dataState) {
            TelephonyManager.DATA_CONNECTED -> "متصل"
            TelephonyManager.DATA_SUSPENDED -> "معلق"
            TelephonyManager.DATA_DISCONNECTED -> "قطع"
            else -> "نامشخص"
        }
        val roaming = if (telephonyManager?.isNetworkRoaming == true) "بله (خارج از شبکه خانگی)" else "خیر"

        val signalDbm = getSignalStrengthDbm()
        val signalAsu = convertDbmToAsu(signalDbm, telephonyManager?.voiceNetworkType ?: 0)

        sbGeneral.append("📱 اپراتور: $operatorName\n")
        sbGeneral.append("🌍 کد شبکه: MCC=$mcc, MNC=$mnc\n")
        sbGeneral.append("🔋 وضعیت سیم: $simState\n")
        sbGeneral.append("📞 نوع شبکه صوتی: $voiceNetworkType\n")
        sbGeneral.append("📡 نوع شبکه داده: $dataNetworkType\n")
        sbGeneral.append("🌐 وضعیت داده: $dataState\n")
        sbGeneral.append("🗺 رومینگ: $roaming\n")

        if (signalDbm == -2300) {
            sbGeneral.append("📶 قدرت سیگنال: قابل خواندن نیست\n")
            sbGeneral.append("📊 ASU: --\n")
        } else {
            sbGeneral.append("📶 قدرت سیگنال: $signalDbm dBm\n")
            sbGeneral.append("📊 ASU: $signalAsu\n")
        }
        sbGeneral.append("🕒 آخرین بروزرسانی: $now")

        generalInfoText.text = sbGeneral.toString()

        if (signalDbm != -2300) {
            when {
                signalDbm < -110 -> mainLayout.setBackgroundColor(Color.RED)
                signalDbm in -100..-90 -> mainLayout.setBackgroundColor(Color.YELLOW)
                else -> mainLayout.setBackgroundColor(Color.WHITE)
            }
        }

        // بخش دکل‌ها
        val towerSb = StringBuilder()
        towerSb.append("=== اطلاعات دکل‌های قابل مشاهده ===\n")
        val cells = getAllCellInfoList()
        if (cells.isNotEmpty()) {
            for ((index, cellData) in cells.withIndex()) {
                towerSb.append("\nدکل ${index + 1}:\n")
                towerSb.append("نوع: ${cellData.type}\n")
                towerSb.append("شناسه سلول (Cell ID): ${cellData.cellId}\n")
                towerSb.append("LAC/TAC: ${cellData.lac}\n")
                towerSb.append("MCC/MNC: ${cellData.mcc}/${cellData.mnc}\n")
                towerSb.append("قدرت سیگنال: ${cellData.signalDbm} dBm / ASU: ${cellData.asu}\n")
                if (cellData.band != null) {
                    towerSb.append("باند فرکانسی: ${cellData.band}\n")
                }
				if (cellData.timingAdvance != null) {
                    towerSb.append("⏱ Timing Advance: ${cellData.timingAdvance}\n")
                }
                if (cellData.distanceMeters != null) {
                    towerSb.append("📍 فاصله تقریبی: ${cellData.distanceMeters} متر\n")
                }
                towerSb.append("━━━━━━━━━━━━━━━━━━━\n")
            }
        } else {
            towerSb.append("هیچ دکلی یافت نشد (احتمالاً مجوز موقعیت داده نشده یا دستگاه در حالت هواپیماست).")
        }
        towerInfoText.text = towerSb.toString()
    }

    private fun getSignalStrengthDbm(): Int {
        val ss = telephonyManager?.signalStrength ?: return -2300
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cellSignals = ss.cellSignalStrengths
            if (cellSignals.isNotEmpty()) {
                cellSignals[0].dbm
            } else {
                -2300
            }
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

    private fun convertDbmToAsu(dbm: Int, voiceType: Int): Int {
        if (dbm == -2300) return -1
        return when (voiceType) {
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE -> (dbm + 113) / 2
            else -> dbm + 141
        }.coerceIn(0, 99)
    }

    private fun getDataNetworkTypeName(): String {
        return when (telephonyManager?.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G HSPA+"
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
            else -> "نامشخص"
        }
    }

    private fun getVoiceNetworkTypeName(): String {
        return when (telephonyManager?.voiceNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR (VoNR)"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE (VoLTE)"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G (UMTS)"
            TelephonyManager.NETWORK_TYPE_GSM -> "2G (GSM)"
            else -> "نامشخص"
        }
    }

    @SuppressLint("MissingPermission")
    private fun getAllCellInfoList(): List<CellData> {
        val cells = mutableListOf<CellData>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return cells
        }
        val allCellInfo = telephonyManager?.allCellInfo ?: return cells

        for (cellInfo in allCellInfo) {
            val ta = getTimingAdvance(cellInfo)
            val distance = ta?.let { calculateDistance(it, cellInfo) }
            when (cellInfo) {
                is CellInfoLte -> {
                    val identity = cellInfo.cellIdentity
                    val signal = cellInfo.cellSignalStrength
                    val dbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) signal.dbm else -2300
                    val asu = if (dbm != -2300) (dbm + 141).coerceIn(0, 99) else -1
                    val band = getLteBandFromEarfcn(identity.earfcn)
                    cells.add(CellData(
                        type = "LTE",
                        cellId = identity.ci.toString(),
                        lac = identity.tac.toString(),
                        mcc = identity.mccString ?: "?",
                        mnc = identity.mncString ?: "?",
                        signalDbm = dbm,
                        asu = asu,
                        band = band,
                        timingAdvance = ta,
                        distanceMeters = distance
                    ))
                }
                is CellInfoWcdma -> {
                    val identity = cellInfo.cellIdentity
                    val signal = cellInfo.cellSignalStrength
                    val dbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) signal.dbm else -2300
                    val asu = if (dbm != -2300) (dbm + 141).coerceIn(0, 99) else -1
                    cells.add(CellData(
                        type = "WCDMA",
                        cellId = identity.cid.toString(),
                        lac = identity.lac.toString(),
                        mcc = identity.mccString ?: "?",
                        mnc = identity.mncString ?: "?",
                        signalDbm = dbm,
                        asu = asu,
                        band = null,
                        timingAdvance = ta,
                        distanceMeters = distance
                    ))
                }
                is CellInfoGsm -> {
                    val identity = cellInfo.cellIdentity
                    val signal = cellInfo.cellSignalStrength
                    val dbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) signal.dbm else -2300
                    val asu = if (dbm != -2300) (dbm + 113) / 2 else -1
                    cells.add(CellData(
                        type = "GSM",
                        cellId = identity.cid.toString(),
                        lac = identity.lac.toString(),
                        mcc = identity.mccString ?: "?",
                        mnc = identity.mncString ?: "?",
                        signalDbm = dbm,
                        asu = asu,
                        band = null,
                        timingAdvance = ta,
                        distanceMeters = distance
                    ))
                }
                is CellInfoNr -> {
				    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				        val identity = cellInfo.cellIdentity
				        val signal = cellInfo.cellSignalStrength
				        val dbm = signal.dbm
				        val asu = if (dbm != -2300) (dbm + 141).coerceIn(0, 99) else -1
				        val band = getNrBandFromNrarfcn(identity.nrarfcn)
				        cells.add(CellData(
				            type = "NR (5G)",
				            cellId = identity.nci.toString(),
				            lac = identity.tac.toString(),
				            mcc = identity.mccString ?: "?",
				            mnc = identity.mncString ?: "?",
				            signalDbm = dbm,
				            asu = asu,
				            band = band,
				            timingAdvance = ta,
				            distanceMeters = distance
				        ))
				    }
				}
            }
        }
        return cells
    }

    private fun getTimingAdvance(cellInfo: CellInfo): Int? {
	    return when (cellInfo) {
	        is CellInfoLte -> {
	            val ta = cellInfo.cellSignalStrength.timingAdvance
	            // TA در LTE می‌تواند از 0 تا 1282 باشد
	            if (ta in 0..1282 && ta != Int.MAX_VALUE) ta else null
	        }
	        is CellInfoNr -> {
	            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
	                try {
	                    // استفاده از getter استاندارد
	                    val taMethod = CellSignalStrengthNr::class.java.getMethod("getTimingAdvance")
	                    val ta = taMethod.invoke(cellInfo.cellSignalStrength) as? Int
	                    if (ta != null && ta in 0..3840) ta else null
	                } catch (e: Exception) {
	                    null
	                }
	            } else {
	                null
	            }
	        }
	        else -> null
	    }
	}

    private fun calculateDistance(ta: Int, cellInfo: CellInfo): Int {
	    // هر واحد TA در LTE تقریباً 78 متر است
	    // در 5G هر واحد TA تقریباً 39 متر است
	    val metersPerTa = when (cellInfo) {
	        is CellInfoLte -> 78
	        is CellInfoNr -> 39
	        else -> 78
	    }
	    return ta * metersPerTa
	}
    
    data class CellData(
        val type: String,
        val cellId: String,
        val lac: String,
        val mcc: String,
        val mnc: String,
        val signalDbm: Int,
        val asu: Int,
        val band: String?,
        val timingAdvance: Int? = null,
        val distanceMeters: Int? = null
    )

    private fun getLteBandFromEarfcn(earfcn: Int): String? {
        if (earfcn <= 0) return null
        return when {
            earfcn in 0..599 -> "B1 (2100)"
            earfcn in 600..1199 -> "B2 (1900)"
            earfcn in 1200..1949 -> "B3 (1800)"
            earfcn in 1950..2399 -> "B4 (1700/2100)"
            earfcn in 2400..2649 -> "B5 (850)"
            earfcn in 2650..2749 -> "B6 (800)"
            earfcn in 2750..3449 -> "B7 (2600)"
            earfcn in 3450..3799 -> "B8 (900)"
            earfcn in 3800..4149 -> "B9 (1800)"
            earfcn in 4150..4749 -> "B10 (1700)"
            earfcn in 4750..4949 -> "B11 (1500)"
            earfcn in 5010..5179 -> "B12 (700)"
            earfcn in 5180..5279 -> "B13 (700)"
            earfcn in 5280..5379 -> "B14 (700)"
            earfcn in 5730..5849 -> "B18 (850)"
            earfcn in 6000..6149 -> "B19 (850)"
            earfcn in 6150..6449 -> "B20 (800)"
            earfcn in 6520..6689 -> "B25 (1900)"
            earfcn in 8040..8689 -> "B28 (700)"
            earfcn in 37750..38249 -> "B38 (2600)"
            earfcn in 38250..38649 -> "B39 (1900)"
            earfcn in 38650..39649 -> "B40 (2300)"
            earfcn in 39650..41589 -> "B41 (2500)"
            else -> "EARFCN $earfcn"
        }
    }

    private fun getNrBandFromNrarfcn(nrarfcn: Int): String? {
        if (nrarfcn <= 0) return null
        return when {
            nrarfcn in 422000..434000 -> "n1 (2100)"
            nrarfcn in 361000..376000 -> "n3 (1800)"
            nrarfcn in 393000..405000 -> "n5 (850)"
            nrarfcn in 524000..538000 -> "n7 (2600)"
            nrarfcn in 620000..653000 -> "n28 (700)"
            nrarfcn in 636000..646000 -> "n38 (2600)"
            nrarfcn in 460000..480000 -> "n78 (3500)"
            else -> "NR-ARFCN $nrarfcn"
        }
    }

    override fun onDestroy() {
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        super.onDestroy()
    }
}
