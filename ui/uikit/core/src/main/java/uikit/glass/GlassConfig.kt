package uikit.glass

object GlassConfig {

    var isEnabled: Boolean = false
        private set

    private var isLatched = false

    fun latch(enabled: Boolean) {
        if (!isLatched) {
            isLatched = true
            isEnabled = enabled
        }
    }
}
