package com.example.decoder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.round

class ArrivalEstimator(
    var defaultKm: Double? = null,
    var maxSeconds: Double = 6.0 * 3600.0,
    routeKmInitialMap: Map<String, Double> = emptyMap()
) {
    var downIncreases: Boolean = true
    val routeKmMap = HashMap<String, Double>()
    val knownRoutes = ArrayList<String>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        for ((r, km) in routeKmInitialMap) {
            setRouteKm(r, km)
        }
    }

    companion object {
        private val INVALID_CHARS_PATTERN = Pattern.compile("[\\*U\\(\\)<>\\[\\]{}\\\\/|;:,.，。！？?！@#$%^&_=+`~]")
        private val VALID_NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9\\-\\s]+$")
        private val HAS_ALPHANUM_CHINESE = Pattern.compile("[\\u4e00-\\u9fa5A-Za-z0-9]")

        fun sanitizeRoute(route: String?): String {
            if (route == null) return ""
            val r = route.replace("\u0000", "").trim()
            if (r.isEmpty() || r in listOf("----", "---", "未知", "等待信号...")) return ""
            return r
        }

        fun formatKm(km: Double?): String {
            return if (km != null) String.format(Locale.US, "%06.1fKM", km) else "---"
        }

        fun parseKm(v: Any?): Double? {
            if (v == null) return null
            val s = v.toString().uppercase().replace("KM", "").trim()
            if (s.isEmpty() || s in listOf("---", "---.-", "----")) return null
            val km = s.toDoubleOrNull() ?: return null
            if (km < 0.0 || km > 9999.9) return null
            return round(km * 10.0) / 10.0
        }

        fun isRouteValid(route: String?): Boolean {
            val r = sanitizeRoute(route)
            if (r.isEmpty() || r.length > 24) return false
            if (INVALID_CHARS_PATTERN.matcher(r).find()) return false
            if (!HAS_ALPHANUM_CHINESE.matcher(r).find()) return false
            return VALID_NAME_PATTERN.matcher(r).matches()
        }
    }

    fun addKnownRoute(route: String): String {
        val r = sanitizeRoute(route)
        if (!isRouteValid(r)) return ""
        if (!knownRoutes.contains(r)) {
            knownRoutes.add(r)
            if (knownRoutes.size > 20) {
                knownRoutes.removeAt(0)
            }
        }
        return r
    }

    fun getKmForRoute(route: String?): Double? {
        val r = sanitizeRoute(route)
        if (r.isNotEmpty() && routeKmMap.containsKey(r)) {
            return routeKmMap[r]
        }
        return defaultKm
    }

    fun setRouteKm(route: String?, km: Double?): Boolean {
        val r = sanitizeRoute(route)
        if (r.isEmpty() || km == null) return false
        routeKmMap[r] = km
        addKnownRoute(r)
        return true
    }

    fun removeRouteKm(route: String?) {
        val r = sanitizeRoute(route)
        if (r.isNotEmpty()) {
            routeKmMap.remove(r)
        }
    }

    fun estimate(
        train: String?,
        direction: String?,
        speedStr: String?,
        positionStr: String?,
        routeStr: String?,
        goodData: Boolean = true,
        nowEpochMs: Long = System.currentTimeMillis()
    ): EtaInfo {
        val route = sanitizeRoute(routeStr)
        val routeGood = goodData && isRouteValid(route)
        if (routeGood) {
            addKnownRoute(route)
        }

        if (!goodData) {
            return EtaInfo(etaStatus = "错包忽略", etaTrain = train ?: "----", etaRoute = route)
        }
        if (route.isEmpty()) {
            return EtaInfo(etaStatus = "线路未知", etaTrain = train ?: "----", etaRoute = "----")
        }

        val userKm = getKmForRoute(route)
            ?: return EtaInfo(etaStatus = "未设置本站位置", etaTrain = train ?: "----", etaRoute = route)

        if (train.isNullOrBlank() || train == "----") {
            return EtaInfo(etaStatus = "等待车次", etaTrain = "----", etaRoute = route)
        }
        if (direction != "上行" && direction != "下行") {
            return EtaInfo(etaStatus = "方向未知", etaTrain = train, etaRoute = route)
        }

        val speed = speedStr?.replace("km/h", "")?.trim()?.toDoubleOrNull()
        if (speed == null || speed <= 0.0) {
            return EtaInfo(etaStatus = "速度无效", etaTrain = train, etaRoute = route)
        }

        val trainKm = parseKm(positionStr)
            ?: return EtaInfo(etaStatus = "公里标无效", etaTrain = train, etaRoute = route)

        val distanceKm = if (downIncreases) {
            if (direction == "下行") userKm - trainKm else trainKm - userKm
        } else {
            if (direction == "下行") trainKm - userKm else userKm - trainKm
        }

        if (distanceKm < -0.02) {
            return EtaInfo(etaStatus = "远离/已过", etaTrain = train, etaRoute = route, etaDistanceKm = distanceKm)
        }

        val nonNegDistance = max(0.0, distanceKm)
        val etaSec = (nonNegDistance / speed) * 3600.0
        if (etaSec > maxSeconds) {
            return EtaInfo(
                etaSeconds = null,
                etaTime = "--:--:--",
                etaDistanceKm = nonNegDistance,
                etaStatus = "ETA过大",
                etaTrain = train,
                etaRoute = route
            )
        }

        val etaTime = timeFormat.format(Date(nowEpochMs + (etaSec * 1000).toLong()))
        val status = if (etaSec <= 10.0) "即将到达" else "接近"

        return EtaInfo(
            etaSeconds = etaSec,
            etaTime = etaTime,
            etaDistanceKm = nonNegDistance,
            etaStatus = status,
            etaTrain = train,
            etaRoute = route
        )
    }
}
