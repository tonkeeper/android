package com.tonapps.core.mvcc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch

sealed interface Action {
    data object PreLoad : Action
    data object Refresh : Action
    data object Load : Action
}

// Init first data
// Load first page from cache
// Load first page from remote
// Refresh
// Additional load
class MvccFeature : ViewModel() {

    val main = viewModelScope
    val stateD = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val state = CoroutineScope(viewModelScope.coroutineContext + stateD)
    val io = CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO)
    val default = CoroutineScope(viewModelScope.coroutineContext + Dispatchers.Default)

    val mvcc = Mvcc()
    val repo = Repo()

    suspend fun execute(action: Action) {
        state.launch {
            when (action) {
                Action.PreLoad -> execute {
                    val task = repo.loadCommonData()
                    setState { task }

                    val firstPage = repo.loadFirstPageLocal()
                    val secondPage = io.async { repo.loadFirstPageRemote() }
                    setState { firstPage }

                    val result = secondPage.await()
                    setState { result }
                }
                Action.Load -> execute {
                    val secondPage = repo.loadNextPage()
                    setState { secondPage }
                }
                Action.Refresh -> reset {
                    val task = io.async { repo.loadCommonData() }
                    setState { "Refresh" }
                }
            }
        }
    }

    suspend fun reset(invoke: suspend Int.() -> Unit) {
        coroutineScope {
            val version = mvcc.acquire()
            mvcc.commit(version - 1)

            try {
                invoke(version)
            } finally {
                mvcc.commit(version)
            }
        }
    }

    suspend fun Int.setState(builder: () -> String) {
        mvcc.await(this)
        builder()
    }

    suspend fun execute(invoke: suspend Int.() -> Unit) {
        coroutineScope {
            val version = mvcc.acquire()

            try {
                invoke(version)
            } finally {
                mvcc.commit(version)
            }
        }
    }

    suspend fun Int.await(after: suspend () -> Unit) {
        mvcc.await(this)
        after()
    }

    fun handleLoad() {

    }
}

class Mvcc {

    private val awaited = AtomicInt(0)
    private val commited = AtomicInt(0)
    private val awaiter = HashMap<Int, Channel<Unit>>()

    fun acquire(): Int {
        return awaited.incrementAndFetch()
    }

    fun commit(version: Int) {
        commited.compareAndSet(version - 1, version)
        awaiter[version]?.trySend(Unit)
    }

    suspend fun await(version: Int) {
        val new = Channel<Unit>() // TODO if nore - fail
        awaiter[version - 1] = new
        new.receive()
    }
}

class Repo {
    suspend fun loadCommonData(): String {
        return "Result"
    }

    suspend fun loadFirstPageLocal(): String {
        return "Result"
    }

    suspend fun loadFirstPageRemote(): String {
        return "Result"
    }

    suspend fun loadNextPage(): String {
        return "Result"
    }
}