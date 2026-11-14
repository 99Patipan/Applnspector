@file:OptIn(ExperimentalMaterial3Api::class)

package com.personal.appinspector

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmt(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
fun Root(vm: MainVM = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<AppItem?>(null) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }
    BackHandler(enabled = sel != null) { sel = null }

    val titles = listOf("แอป", "Tracker", "ความเสี่ยง", "ประวัติ")
    val emojis = listOf("📱", "🕵️", "🛡️", "🕘")

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (sel == null) NavigationBar {
                titles.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(emojis[i]) }, label = { Text(t) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            val s = sel
            if (s != null) DetailScreen(vm, s) { sel = null }
            else when (tab) {
                0 -> AppsTab(vm) { sel = it }
                1 -> TrackerTab(vm) { sel = it }
                2 -> SecurityTab(vm) { sel = it }
                else -> HistoryTab(vm) { sel = it }
            }
        }
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    if (bmp != null) Image(bmp, null, Modifier.size(size)) else Box(Modifier.size(size))
}

@Composable
fun AppRow(a: AppItem, trailing: String? = null, sub: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(a.pkg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                sub ?: a.pkg, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) Text(trailing, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AppsTab(vm: MainVM, onOpen: (AppItem) -> Unit) {
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var byUpdate by remember { mutableStateOf(false) }
    val list = remember(vm.apps, q, filter, byUpdate) {
        vm.apps.filter {
            (filter == 2 || (filter == 0 && !it.isSystem) || (filter == 1 && it.isSystem)) &&
                (q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true))
        }.let { l -> if (byUpdate) l.sortedByDescending { it.lastUpdate } else l }
    }
    Column {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            label = { Text("ค้นหาชื่อแอป / package") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ผู้ใช้", "ระบบ", "ทั้งหมด").forEachIndexed { i, t ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(t) })
            }
            FilterChip(selected = byUpdate, onClick = { byUpdate = !byUpdate }, label = { Text("อัปเดตล่าสุด") })
        }
        Text("${list.size} แอป", Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.bodySmall)
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn {
            items(list, key = { it.pkg }) { a ->
                AppRow(a, sub = if (byUpdate) "${fmt(a.lastUpdate)} · v${a.versionName}" else a.pkg) { onOpen(a) }
            }
        }
    }
}

@Composable
fun ScanHeader(vm: MainVM) {
    Column(Modifier.padding(12.dp)) {
        Button(onClick = { vm.scanAll() }, enabled = !vm.scanning) {
            Text(if (vm.scanning) "กำลังสแกน…" else "สแกนแอปผู้ใช้ทั้งหมด")
        }
        if (vm.scanning) LinearProgressIndicator(
            progress = { vm.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

@Composable
fun TrackerTab(vm: MainVM, onOpen: (AppItem) -> Unit) {
    val scanned = vm.apps.filter { vm.analyses.containsKey(it.pkg) }
    val sorted = scanned.sortedBy { vm.analyses[it.pkg]!!.privacyScore }
    val top = scanned.flatMap { vm.analyses[it.pkg]!!.trackers }
        .groupingBy { it.name }.eachCount().entries.sortedByDescending { it.value }.take(5)
    LazyColumn {
        item { ScanHeader(vm) }
        if (top.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Tracker ที่พบบ่อยสุด", fontWeight = FontWeight.Bold)
                    top.forEach { Text("• ${it.key} — ${it.value} แอป") }
                }
            }
        }
        if (scanned.isEmpty() && !vm.scanning) item {
            Text("กดสแกนเพื่อตรวจ Tracker และคะแนนความเป็นส่วนตัว (ยิ่งต่ำยิ่งมี Tracker มาก)", Modifier.padding(16.dp))
        }
        items(sorted, key = { it.pkg }) { a ->
            val an = vm.analyses[a.pkg]!!
            AppRow(a, trailing = "${an.privacyScore}", sub = "${an.trackers.size} tracker") { onOpen(a) }
        }
    }
}

@Composable
fun SecurityTab(vm: MainVM, onOpen: (AppItem) -> Unit) {
    val sorted = vm.apps.filter { vm.analyses.containsKey(it.pkg) }
        .sortedByDescending { vm.analyses[it.pkg]!!.riskScore }
    LazyColumn {
        item { ScanHeader(vm) }
        if (sorted.isEmpty() && !vm.scanning) item {
            Text("กดสแกนเพื่อประเมินความเสี่ยงจากสิทธิ์ Accessibility, SMS, Contacts และอื่น ๆ", Modifier.padding(16.dp))
        }
        items(sorted, key = { it.pkg }) { a ->
            val an = vm.analyses[a.pkg]!!
            AppRow(a, trailing = "${an.riskScore}", sub = "ความเสี่ยง${riskLabel(an.riskScore)} · ${an.risks.size} ข้อ") { onOpen(a) }
        }
    }
}

@Composable
fun HistoryTab(vm: MainVM, onOpen: (AppItem) -> Unit) {
    val byPkg = vm.apps.associateBy { it.pkg }
    val recent = vm.apps.filter { !it.isSystem }.sortedByDescending { it.lastUpdate }.take(30)
    LazyColumn {
        item { Text("บันทึกการเปลี่ยนแปลง (ตรวจเมื่อเปิดแอป)", Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
        if (vm.events.isEmpty()) item {
            Text("ยังไม่มีเหตุการณ์ — แอปจะบันทึกการติดตั้ง/อัปเดต/ถอนแอปที่เกิดขึ้นหลังจากนี้", Modifier.padding(horizontal = 16.dp))
        }
        items(vm.events) { e ->
            val icon = when (e.type) { "install" -> "➕ ติดตั้ง"; "update" -> "⬆️ อัปเดต"; else -> "❌ ถอนการติดตั้ง" }
            Column(Modifier.fillMaxWidth().clickable(enabled = byPkg[e.pkg] != null) { byPkg[e.pkg]?.let(onOpen) }.padding(16.dp, 6.dp)) {
                Text("$icon  ${e.label}")
                Text("${fmt(e.time)}  ${e.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Text("ติดตั้ง/อัปเดตล่าสุดตามระบบ", Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
        items(recent, key = { "r" + it.pkg }) { a ->
            AppRow(a, sub = "${fmt(a.lastUpdate)} · v${a.versionName}") { onOpen(a) }
        }
    }
}

@Composable
fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, Modifier.weight(0.38f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(v, Modifier.weight(0.62f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
}

@Composable
fun DetailScreen(vm: MainVM, a: AppItem, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    LaunchedEffect(a.pkg) { vm.ensureAnalysis(a) }
    val an = vm.analyses[a.pkg]
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri: Uri? -> if (uri != null) vm.exportTo(a, uri) }

    fun copy(s: String) { clip.setText(AnnotatedString(s)); vm.message = "คัดลอกแล้ว" }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← กลับ") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(a.pkg, 56.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(a.label, style = MaterialTheme.typography.headlineSmall)
                    Text(a.pkg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { copy(a.pkg) })
                    Text("แตะ package เพื่อคัดลอก", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.backup(a) }) { Text("สำรอง APK") }
                OutlinedButton(onClick = { launcher.launch("${a.pkg}.apk") }) { Text("ส่งออกไปที่…") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let { ctx.startActivity(it) } }) { Text("เปิดแอป") }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
                }) { Text("ตั้งค่าแอป") }
            }
        }
        item {
            val size = (listOf(a.sourceDir) + a.splits).sumOf { File(it).length() }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    InfoRow("เวอร์ชัน", "${a.versionName} (${a.versionCode})")
                    InfoRow("Target SDK", "${a.targetSdk} (Android ${androidName(a.targetSdk)})")
                    InfoRow("Min SDK", "${a.minSdk} (Android ${androidName(a.minSdk)})")
                    InfoRow("เข้ากับเครื่องนี้", if (Build.VERSION.SDK_INT >= a.minSdk) "ใช่" else "ไม่")
                    InfoRow("แหล่งติดตั้ง", installerName(a.installer))
                    InfoRow("ติดตั้งครั้งแรก", fmt(a.firstInstall))
                    InfoRow("อัปเดตล่าสุด", fmt(a.lastUpdate))
                    InfoRow("ประเภท", if (a.isSystem) "แอประบบ" else "แอปผู้ใช้")
                    InfoRow("ขนาด APK", "%.1f MB (%d ไฟล์)".format(size / 1048576.0, 1 + a.splits.size))
                    InfoRow("ตำแหน่ง", a.sourceDir)
                }
            }
        }
        if (an == null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else {
            item { SectionTitle("🛡️ ความเสี่ยง: ${an.riskScore}/100 (${riskLabel(an.riskScore)})") }
            if (an.risks.isEmpty()) item { Text("ไม่พบสัญญาณความเสี่ยงเด่น") }
            items(an.risks) { r ->
                Text((if (r.level == 3) "🔴 " else if (r.level == 2) "🟠 " else "🟡 ") + r.text)
            }
            item { Text("หมายเหตุ: คะแนนเป็นการประเมินจากสิทธิ์/คอนฟิก ไม่ได้ยืนยันว่าแอปปลอดภัยหรือเป็นอันตราย", style = MaterialTheme.typography.bodySmall) }

            item { SectionTitle("🕵️ Privacy score: ${an.privacyScore}/100") }
            if (an.trackers.isEmpty()) item { Text("ไม่พบ Tracker ที่รู้จัก (แอปที่ obfuscate หนักอาจตรวจไม่เจอ)") }
            items(an.trackers) { Text("• ${it.name}  (${it.category})") }

            item { SectionTitle("🔑 Signature") }
            if (an.certs.isEmpty()) item { Text("อ่านใบรับรองไม่ได้") }
            items(an.certs) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        if (c.subject.isNotBlank()) Text(c.subject, style = MaterialTheme.typography.bodySmall)
                        Text("SHA-256 (แตะเพื่อคัดลอก)", style = MaterialTheme.typography.bodySmall)
                        Text(c.sha256, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.clickable { copy(c.sha256) })
                        Text("SHA-1", style = MaterialTheme.typography.bodySmall)
                        Text(c.sha1, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.clickable { copy(c.sha1) })
                    }
                }
            }

            item { SectionTitle("⚙️ Native libraries / CPU") }
            if (an.libs.isEmpty()) item { Text("ไม่มีไฟล์ .so (โค้ด Java/Kotlin ล้วน)") }
            items(an.libs.entries.toList()) { (abi, names) ->
                Text("$abi — ${names.size} ไฟล์\n" + names.take(10).joinToString(", ") + if (names.size > 10) " …" else "",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (an.frameworks.isNotEmpty()) item { Text("Framework ที่พบ: " + an.frameworks.joinToString(", ")) }
            val has64 = an.libs.keys.any { it == "arm64-v8a" || it == "x86_64" }
            val has32 = an.libs.keys.any { it == "armeabi-v7a" || it == "x86" || it == "armeabi" }
            if (has32 && !has64) item { Text("⚠️ มีเฉพาะ native lib แบบ 32-bit (ไม่รองรับ 64-bit)") }

            item { SectionTitle("📋 สิทธิ์ที่ขอ (${an.perms.size}, อนุมัติ ${an.perms.count { it.granted }})") }
            items(an.perms) { p ->
                Text(
                    (if (p.granted) "✅ " else "⬜ ") + (if (p.risk != null) "⚠️ " else "") + p.name.removePrefix("android.permission."),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
