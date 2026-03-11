package com.tonapps.log.targets.console

import android.util.Log
import com.tonapps.log.L
import com.tonapps.log.LogTarget

open class ConsoleLogTarget : LogTarget {

    override fun log(type: L.LogType, tag: String?, msg: String?) {
        Log.println(type.toLog(), tag, msg ?: "EMPTY_MSG")
    }
}
