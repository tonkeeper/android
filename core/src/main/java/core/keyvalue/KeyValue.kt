package core.keyvalue

import android.content.Context

class KeyValue(
    context: Context,
    name: String
): BaseKeyValue() {

    override val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
}