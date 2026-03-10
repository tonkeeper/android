package com.tonapps.log

import java.io.File

interface LogTarget {

    fun log(type: L.LogType, tag: String?, msg: String?)

    fun log(type: L.LogType, tag: String?, msg: String?, tr: Throwable?) {
        log(type, tag, msg + '\n'.toString() + tr?.stackTraceToString())
    }

    fun files(): List<File> { return emptyList() }

    fun prepare(config: LoggerConfig) {}

    fun release() {}
}
