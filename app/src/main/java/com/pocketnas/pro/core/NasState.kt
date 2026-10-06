package com.pocketnas.pro.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局状态：内核进程运行状态、日志缓冲、登录信息。
 * 单进程内共享（Application 级）。
 */
object NasState {

    /** 内核服务是否运行中 */
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** 最近错误信息 */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** 日志行缓冲（上限 500 行） */
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private const val MAX_LOG_LINES = 500
    private val lock = Any()

    fun setRunning(value: Boolean) {
        _running.value = value
    }

    fun setError(msg: String?) {
        _lastError.value = msg
    }

    /** 追加一行日志：内存缓冲（UI 实时）+ 文件落盘（LogStore，可调取） */
    fun appendLog(line: String) {
        synchronized(lock) {
            val trimmed = line.trimEnd('\n', '\r')
            if (trimmed.isEmpty()) return
            val cur = _logs.value.toMutableList()
            cur.add(trimmed)
            while (cur.size > MAX_LOG_LINES) cur.removeAt(0)
            _logs.value = cur
        }
        LogStore.log("NAS", line)
    }

    fun clearLogs() {
        synchronized(lock) {
            _logs.value = emptyList()
        }
    }
}
