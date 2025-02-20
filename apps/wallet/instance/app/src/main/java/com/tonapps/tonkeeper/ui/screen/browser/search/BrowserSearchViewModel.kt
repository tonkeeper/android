package com.tonapps.tonkeeper.ui.screen.browser.search

import android.app.Application
import android.net.Uri
import android.util.Log
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.toUriOrNull
import com.tonapps.network.get
import com.tonapps.tonkeeper.client.safemode.SafeModeClient
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.browser.search.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.core.SearchEngine
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray

class BrowserSearchViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val browserRepository: BrowserRepository,
    private val api: API,
    private val safeModeClient: SafeModeClient,
): BaseWalletVM(app) {

    private val _queryFlow = MutableEffectFlow<String>()

    @OptIn(FlowPreview::class)
    val uiItemsFlow = _queryFlow.asSharedFlow()
        .map { it.trim().lowercase() }
        .debounce(300)
        .map(::search)
        .flowOn(Dispatchers.IO)

    fun query(value: String) {
        _queryFlow.tryEmit(value)
    }

    fun isScamUri(url: String): Boolean {
        return isScamUri(url.toUriOrNull())
    }

    fun isScamUri(uri: Uri?): Boolean {
        return uri?.let {
            safeModeClient.isHasScamUris(it)
        } ?: false
    }

    private suspend fun search(query: String): List<Item> = withContext(Dispatchers.IO) {
        if (query.isEmpty()) {
            return@withContext emptyList()
        }

        val uri = uri(query)
        val apps = browserRepository.search(
            country = settingsRepository.country,
            query = query,
            locale = settingsRepository.getLocale()
        ).filter { !safeModeClient.isHasScamUris(it.url) }

        val appsCount = if (uri == null) {
            apps.size
        } else {
            apps.size + 1
        }

        val items = mutableListOf<Item>()
        if (isScamUri(uri)) {
            items
        } else {
            for (index in 0 until appsCount) {
                val position = ListCell.getPosition(appsCount, index)
                if (index == 0 && uri != null) {
                    items.add(Item.Link(position, uri.toString(), uri.host!!))
                }
                val appIndex = index - if (uri != null) 1 else 0
                if (appIndex in apps.indices) {
                    items.add(Item.App(position, apps[appIndex]))
                }
            }

            items + searchBy(query)
        }
    }

    private fun uri(query: String): Uri? {
        if (isDomain(query)) {
            return Uri.parse("https://$query")
        }
        if (query.startsWith("http://")) {
            return uri(query.replace("http://", "https://"))
        }
        return try {
            val uri = Uri.parse(query)
            if (uri.scheme != "https") {
                return null
            }
            if (uri.host != null) {
                uri
            } else {
                null
            }
        } catch (ignored: Throwable) {
            null
        }
    }

    fun createSearchUrl(query: String): Uri {
        val searchEngine = settingsRepository.searchEngine
        return if (searchEngine == SearchEngine.GOOGLE) {
            Uri.parse("https://www.google.com/search?q=$query")
        } else {
            Uri.parse("https://duckduckgo.com/?q=$query")
        }
    }

    private fun searchBy(query: String): List<Item> {
        val searchEngine = settingsRepository.searchEngine
        val result = if (searchEngine == SearchEngine.GOOGLE) {
            searchByGoogle(query)
        } else {
            searchByDuckDuckGo(query)
        }
        val list = result.toMutableList()
        list.add(0, Item.Title(searchEngine.title))
        return list
    }

    private fun searchByDuckDuckGo(query: String): List<Item> {
        val list = mutableListOf<Item.SearchDuckDuckGo>()
        try {
            val uri = duckDuckGoUri(query)
            val result = JSONArray(api.defaultHttpClient.get(uri.toString()))
            val size = result.length()
            for (i in 0 until size) {
                val position = ListCell.getPosition(size, i)
                val text = result.getJSONObject(i).getString("phrase")
                list.add(Item.SearchDuckDuckGo(text, position))
            }
        } catch (ignored: Throwable) {}
        if (list.isEmpty()) {
            list.add(Item.SearchDuckDuckGo(query, ListCell.Position.SINGLE))
        }
        return list
    }

    private fun duckDuckGoUri(query: String): Uri {
        val builder = Uri.Builder()
        builder.scheme("https")
        builder.authority("duckduckgo.com")
        builder.appendPath("ac")
        builder.appendQueryParameter("kl", "wt-wt")
        builder.appendQueryParameter("q", query)
        return builder.build()
    }

    private fun searchByGoogle(query: String): List<Item> {
        val list = mutableListOf<Item.SearchGoogle>()
        try {
            val uri = googleUri(query)
            val result = JSONArray(api.defaultHttpClient.get(uri.toString())).getJSONArray(1)
            val size = result.length()
            for (i in 0 until size) {
                val position = ListCell.getPosition(size, i)
                val text = result.getString(i)
                list.add(Item.SearchGoogle(text, position))
            }
            list.distinctBy { it.query }
        } catch (ignored: Throwable) {}
        if (list.isEmpty()) {
            list.add(Item.SearchGoogle(query, ListCell.Position.SINGLE))
        }
        return list
    }

    private fun googleUri(query: String): Uri {
        val builder = Uri.Builder()
        builder.scheme("https")
        builder.authority("suggestqueries.google.com")
        builder.appendPath("complete")
        builder.appendPath("search")
        builder.appendQueryParameter("q", query)
        builder.appendQueryParameter("client", "firefox")
        return builder.build()
    }

    companion object {
        private val domainRegex = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}\$")

        private fun isDomain(query: String): Boolean {
            return domainRegex.matches(query)
        }

        fun parseIfUrl(query: String): Uri? {
            try {
                val uri = if (query.startsWith("https://")) {
                    Uri.parse(query)
                } else if (isDomain(query)) {
                    Uri.parse("https://$query")
                } else {
                    null
                } ?: return null
                if (uri.host.isNullOrEmpty()) {
                    return null
                }
                return uri
            } catch (e: Throwable) {
                return null
            }
        }
    }
}