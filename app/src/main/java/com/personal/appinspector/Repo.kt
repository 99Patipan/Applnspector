package com.personal.appinspector

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.accessibility.AccessibilityManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

data class AppItem(
    val pkg: String, val label: String, val versionName: String?, val versionCode: Long,
    val targetSdk: Int, val minSdk: Int, val isSystem: Boolean, val installer: String?,
    val firstInstall: Long, val lastUpdate: Long, val sourceDir: String, val splits: List<String>,
    val hasLauncher: Boolean
)

data class PermEntry(val name: String, val granted: Boolean, val risk: String?)
data class CertInfo(val sha256: String, val sha1: String, val subject: String)
data class Risk(val level: Int, val text: String)
data class HistEvent(val time: Long, val type: String, val pkg: String, val label: String, val detail: String)

data class Analysis(
    val perms: List<PermEntry>, val certs: List<CertInfo>,
    val libs: Map<String, List<String>>, val frameworks: List<String>,
    val trackers: List<Tracker>, val risks: List<Risk>,
    val riskScore: Int, val privacyScore: Int
)

// สิทธิ์อ่อนไหว: น้ำหนักความเสี่ยง + คำอธิบาย
val SENSITIVE: Map<String, Pair<Int, String>> = mapOf(
    "android.permission.READ_SMS" to (15 to "อ่าน SMS"),
    "android.permission.RECEIVE_SMS" to (12 to "ดักรับ SMS"),
    "android.permission.SEND_SMS" to (12 to "ส่ง SMS"),
    "android.permission.READ_CONTACTS" to (8 to "อ่านรายชื่อติดต่อ"),
    "android.permission.READ_CALL_LOG" to (12 to "อ่านประวัติการโทร"),
    "android.permission.CALL_PHONE" to (5 to "โทรออกโดยตรง"),
    "android.permission.RECORD_AUDIO" to (8 to "บันทึกเสียง/ไมโครโฟน"),
    "android.permission.CAMERA" to (5 to "ใช้กล้อง"),
    "android.permission.ACCESS_FINE_LOCATION" to (6 to "ตำแหน่งแม่นยำ"),
    "android.permission.ACCESS_BACKGROUND_LOCATION" to (10 to "ตำแหน่งขณะอยู่เบื้องหลัง"),
    "android.permission.SYSTEM_ALERT_WINDOW" to (12 to "แสดงทับแอปอื่น (Overlay)"),
    "android.permission.REQUEST_INSTALL_PACKAGES" to (10 to "ติดตั้งแอปอื่น"),
    "android.permission.MANAGE_EXTERNAL_STORAGE" to (10 to "เข้าถึงไฟล์ทั้งหมด"),
    "android.permission.READ_PHONE_STATE" to (4 to "อ่านสถานะ/ข้อมูลเครื่อง"),
    "android.permission.QUERY_ALL_PACKAGES" to (4 to "ดูรายชื่อแอปทั้งหมด"),
    "android.permission.PACKAGE_USAGE_STATS" to (8 to "ดูสถิติการใช้งานแอป"),
    "android.permission.BIND_DEVICE_ADMIN" to (20 to "Device Admin")
)

fun installerName(p: String?): String = when (p) {
    null -> "ไม่ทราบ / ADB / sideload"
    "com.android.vending" -> "Google Play Store"
    "com.sec.android.app.samsungapps" -> "Galaxy Store"
    "com.amazon.venezia" -> "Amazon Appstore"
    "com.android.packageinstaller", "com.google.android.packageinstaller",
    "com.google.android.apps.nbu.files" -> "ติดตั้งจากไฟล์ APK (sideload)"
    else -> p
}

fun androidName(api: Int): String = when (api) {
    36 -> "16"; 35 -> "15"; 34 -> "14"; 33 -> "13"; 32, 31 -> "12"; 30 -> "11"
    29 -> "10"; 28 -> "9"; 27, 26 -> "8"; 25, 24 -> "7"; 23 -> "6"; else -> "API $api"
}

fun riskLabel(s: Int) = if (s < 20) "ต่ำ" else if (s < 50) "ปานกลาง" else "สูง"

class Repo(private val ctx: Context) {
    private val pm get() = ctx.packageManager
    private val dexRegex = Regex("classes\\d*\\.dex")

    private val accessibilityPkgs: Set<String> by lazy {
        try {
            val am = ctx.getSystemService(AccessibilityManager::class.java)
            am.installedAccessibilityServiceList.map { it.resolveInfo.serviceInfo.packageName }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    @Suppress("DEPRECATION")
    private fun pkgInfo(pkg: String, flags: Int): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33)
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getPackageInfo(pkg, flags)
    } catch (e: Exception) { null }

    @Suppress("DEPRECATION")
    private fun installerOf(pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else pm.getInstallerPackageName(pkg)
    } catch (e: Exception) { null }

    @Suppress("DEPRECATION")
    fun loadApps(): List<AppItem> {
        val list = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        else pm.getInstalledPackages(0)
        val launcherPkgs = launcherPackages()
        return list.mapNotNull { p ->
            val ai = p.applicationInfo ?: return@mapNotNull null
            AppItem(
                pkg = p.packageName, label = ai.loadLabel(pm).toString(),
                versionName = p.versionName, versionCode = p.longVersionCode,
                targetSdk = ai.targetSdkVersion, minSdk = ai.minSdkVersion,
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                installer = installerOf(p.packageName),
                firstInstall = p.firstInstallTime, lastUpdate = p.lastUpdateTime,
                sourceDir = ai.sourceDir, splits = ai.splitSourceDirs?.toList() ?: emptyList(),
                hasLauncher = p.packageName in launcherPkgs
            )
        }.sortedBy { it.label.lowercase() }
    }

    // แอปที่มี launcher activity (ไอคอนบนหน้าจอ) ที่เปิดใช้งานอยู่
    @Suppress("DEPRECATION")
    private fun launcherPackages(): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val l = if (Build.VERSION.SDK_INT >= 33)
            pm.queryIntentActivities(i, PackageManager.ResolveInfoFlags.of(0))
        else pm.queryIntentActivities(i, 0)
        return l.map { it.activityInfo.packageName }.toSet()
    }

    private fun hash(alg: String, b: ByteArray) =
        MessageDigest.getInstance(alg).digest(b).joinToString(":") { "%02X".format(it) }

    fun analyze(a: AppItem): Analysis {
        val pi = pkgInfo(a.pkg, PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES)

        // --- Permissions ---
        val names = pi?.requestedPermissions ?: emptyArray()
        val flags = pi?.requestedPermissionsFlags ?: IntArray(0)
        val perms = names.mapIndexed { i, n ->
            val granted = i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            PermEntry(n, granted, SENSITIVE[n]?.second)
        }.sortedWith(compareBy({ it.risk == null }, { it.name }))

        // --- Certificates ---
        val certs = mutableListOf<CertInfo>()
        try {
            pi?.signingInfo?.apkContentsSigners?.forEach { s ->
                val b = s.toByteArray()
                var subj = ""
                try {
                    val x = CertificateFactory.getInstance("X.509")
                        .generateCertificate(ByteArrayInputStream(b)) as X509Certificate
                    subj = x.subjectX500Principal.name
                } catch (e: Exception) { }
                certs += CertInfo(hash("SHA-256", b), hash("SHA-1", b), subj)
            }
        } catch (e: Exception) { }

        // --- Native libs + Tracker scan (อ่านจากไฟล์ APK) ---
        val libs = sortedMapOf<String, MutableSet<String>>()
        val found = linkedSetOf<Tracker>()
        for (path in listOf(a.sourceDir) + a.splits) {
            try {
                ZipFile(path).use { z ->
                    val en = z.entries()
                    while (en.hasMoreElements()) {
                        val e = en.nextElement()
                        val n = e.name
                        if (n.startsWith("lib/") && n.endsWith(".so")) {
                            val parts = n.split("/")
                            if (parts.size >= 3) libs.getOrPut(parts[1]) { sortedSetOf() }.add(parts.last())
                        } else if (dexRegex.matches(n)) {
                            val s = String(z.getInputStream(e).use { it.readBytes() }, Charsets.ISO_8859_1)
                            for (t in TRACKERS) if (t !in found && t.sigs.any { s.contains(it) }) found += t
                        }
                    }
                }
            } catch (e: Exception) { }
        }
        val allSo = libs.values.flatten().map { it.lowercase() }
        val fw = linkedSetOf<String>()
        for (n in allSo) when {
            "flutter" in n -> fw += "Flutter"
            "reactnativejni" in n -> fw += "React Native"
            "hermes" in n -> fw += "Hermes (JS engine)"
            "libunity" in n || "il2cpp" in n -> fw += "Unity"
            "tensorflowlite" in n -> fw += "TensorFlow Lite"
            "opencv" in n -> fw += "OpenCV"
            "godot" in n -> fw += "Godot"
            "monodroid" in n || "xamarin" in n -> fw += "Xamarin / .NET"
        }

        // --- Security risk ---
        val risks = mutableListOf<Risk>()
        var score = 0
        for (p in perms) {
            val s = SENSITIVE[p.name] ?: continue
            score += if (p.granted) s.first else s.first / 2
            risks += Risk(
                if (s.first >= 12) 3 else if (s.first >= 7) 2 else 1,
                s.second + if (p.granted) " (อนุมัติแล้ว)" else " (ขอไว้ ยังไม่อนุมัติ)"
            )
        }
        if (a.pkg in accessibilityPkgs) {
            score += 25
            risks += Risk(3, "มี Accessibility Service (อ่านหน้าจอ/ควบคุมแอปอื่นได้)")
        }
        val ai = pi?.applicationInfo
        if (ai != null) {
            if ((ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) { score += 15; risks += Risk(3, "แอปเปิด debuggable") }
            if ((ai.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0) { score += 5; risks += Risk(1, "อนุญาตการเชื่อมต่อ HTTP แบบไม่เข้ารหัส") }
        }
        if (a.targetSdk < 28 && !a.isSystem) { score += 10; risks += Risk(2, "Target SDK เก่า (${a.targetSdk}) ข้ามมาตรการความปลอดภัยใหม่ ๆ") }
        if (!a.isSystem && (a.installer == null || installerName(a.installer).startsWith("ติดตั้งจากไฟล์"))) {
            score += 10; risks += Risk(2, "ติดตั้งจากแหล่งที่ไม่ใช่สโตร์")
        }
        if (!a.isSystem && !a.hasLauncher) {
            score += 10
            risks += Risk(2, "ไม่มีไอคอนบนหน้าจอ (แอปซ่อนตัว หรือเป็นแอปทำงานเบื้องหลัง)")
            if (a.pkg in accessibilityPkgs || score >= 30) {
                score += 15
                risks += Risk(3, "ไม่มีไอคอน + สิทธิ์อ่อนไหว/Accessibility: ลักษณะเข้าข่ายแอปซ่อนตัว ควรตรวจสอบ")
            }
        }
        val privacy = (100 - found.sumOf { it.severity * 7 }).coerceIn(0, 100)

        return Analysis(
            perms, certs, libs.mapValues { it.value.toList() }, fw.toList(),
            found.toList(), risks.sortedByDescending { it.level }, score.coerceAtMost(100), privacy
        )
    }

    // --- Export ---
    fun exportToDownloads(a: AppItem): String {
        val dir = "AppBackup"
        val ver = (a.versionName ?: a.versionCode.toString()).replace(Regex("[^A-Za-z0-9._-]"), "_")
        (listOf(a.sourceDir) + a.splits).forEachIndexed { i, path ->
            val name = if (i == 0) "${a.pkg}_$ver.apk" else "${a.pkg}_${ver}_${File(path).name}"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$dir")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("สร้างไฟล์ไม่ได้")
            ctx.contentResolver.openOutputStream(uri)!!.use { out ->
                File(path).inputStream().use { it.copyTo(out) }
            }
        }
        return "Download/$dir"
    }

    fun exportTo(a: AppItem, uri: Uri) {
        ctx.contentResolver.openOutputStream(uri)!!.use { out ->
            File(a.sourceDir).inputStream().use { it.copyTo(out) }
        }
    }

    // --- History (เก็บในเครื่องด้วย SharedPreferences) ---
    fun syncHistory(apps: List<AppItem>): List<HistEvent> {
        val sp = ctx.getSharedPreferences("hist", Context.MODE_PRIVATE)
        val old = sp.getString("snap", "")!!.lines().filter { it.isNotBlank() }.associate {
            val p = it.split("\t")
            p[0] to Pair(p.getOrNull(1)?.toLongOrNull() ?: 0L, p.getOrNull(2) ?: p[0])
        }
        val events = mutableListOf<HistEvent>()
        val arr = try { JSONArray(sp.getString("events", "[]")) } catch (e: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            events += HistEvent(o.getLong("t"), o.getString("type"), o.getString("pkg"), o.getString("label"), o.getString("d"))
        }
        if (old.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val cur = apps.associateBy { it.pkg }
            for (a in apps) {
                val o = old[a.pkg]
                if (o == null) events += HistEvent(now, "install", a.pkg, a.label, "v${a.versionName}" + if (!a.isSystem && !a.hasLauncher) " · ไม่มีไอคอน 👻" else "")
                else if (o.first != a.versionCode) events += HistEvent(now, "update", a.pkg, a.label, "${o.first} → ${a.versionCode}")
            }
            for ((pkg, o) in old) if (pkg !in cur) events += HistEvent(now, "remove", pkg, o.second, "")
        }
        val trimmed = events.sortedByDescending { it.time }.take(500)
        val out = JSONArray()
        trimmed.forEach {
            out.put(JSONObject().put("t", it.time).put("type", it.type).put("pkg", it.pkg).put("label", it.label).put("d", it.detail))
        }
        sp.edit()
            .putString("snap", apps.joinToString("\n") { "${it.pkg}\t${it.versionCode}\t${it.label}" })
            .putString("events", out.toString())
            .apply()
        return trimmed
    }
}
