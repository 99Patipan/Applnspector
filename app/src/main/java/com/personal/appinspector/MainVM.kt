package com.personal.appinspector

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainVM(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)

    var apps by mutableStateOf<List<AppItem>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var events by mutableStateOf<List<HistEvent>>(emptyList()); private set
    val analyses = mutableStateMapOf<String, Analysis>()
    var scanning by mutableStateOf(false); private set
    var progress by mutableStateOf(0f); private set
    var message by mutableStateOf<String?>(null)

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            loading = true
            val l = withContext(Dispatchers.IO) { repo.loadApps() }
            apps = l
            events = withContext(Dispatchers.IO) { repo.syncHistory(l) }
            loading = false
        }
    }

    fun ensureAnalysis(a: AppItem) {
        if (analyses.containsKey(a.pkg)) return
        viewModelScope.launch {
            analyses[a.pkg] = withContext(Dispatchers.IO) { repo.analyze(a) }
        }
    }

    fun scanAll() {
        if (scanning) return
        viewModelScope.launch {
            scanning = true
            progress = 0f
            val targets = apps.filter { !it.isSystem }
            targets.forEachIndexed { i, a ->
                if (!analyses.containsKey(a.pkg)) {
                    analyses[a.pkg] = withContext(Dispatchers.IO) { repo.analyze(a) }
                }
                progress = (i + 1f) / targets.size
            }
            scanning = false
        }
    }

    fun backup(a: AppItem) {
        viewModelScope.launch {
            message = try {
                val d = withContext(Dispatchers.IO) { repo.exportToDownloads(a) }
                "สำรอง APK แล้วที่ $d"
            } catch (e: Exception) { "ล้มเหลว: ${e.message}" }
        }
    }

    fun exportTo(a: AppItem, uri: Uri) {
        viewModelScope.launch {
            message = try {
                withContext(Dispatchers.IO) { repo.exportTo(a, uri) }
                "ส่งออกแล้ว (base APK)"
            } catch (e: Exception) { "ล้มเหลว: ${e.message}" }
        }
    }
}
