package core

@Deprecated("Use Flow from kotlinx.coroutines")
object EventBus {

    private val subscribers = mutableMapOf<Class<*>, MutableSet<(BaseEvent) -> Unit>>()

    fun <T : BaseEvent> subscribe(eventClass: Class<T>, action: (T) -> Unit) {
        if (!subscribers.containsKey(eventClass)) {
            subscribers[eventClass] = mutableSetOf()
        }
        subscribers[eventClass]?.add(action as (BaseEvent) -> Unit)
    }

    fun <T : BaseEvent> unsubscribe(eventClass: Class<T>, action: (T) -> Unit) {
        subscribers[eventClass]?.remove(action as (BaseEvent) -> Unit)
    }

    fun <T : BaseEvent> post(event: T) {
        subscribers[event::class.java]?.forEach {
            it.invoke(event)
        }
    }


}