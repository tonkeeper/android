package com.tonapps.tonkeeper.ui.screen.country

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.extensions.countryEmoji
import com.tonapps.tonkeeper.extensions.countryName
import com.tonapps.tonkeeper.extensions.getNormalizeCountryFlow
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.country.list.Item
import com.tonapps.uikit.flag.getFlagDrawable
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.asCircle
import uikit.extensions.drawable
import java.util.Locale

class CountryPickerViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val api: API,
): BaseWalletVM(app) {

    private data class Country(
        val code: String,
        val name: String,
        val emoji: String,
    ) {
        constructor(code: String) : this(
            code,
            code.countryName,
            code.countryEmoji
        )

        fun contains(query: String) = name.contains(query, ignoreCase = true) || code.contains(query, ignoreCase = true) || emoji.contains(query)
    }

    private val _countriesFlow = MutableStateFlow<List<String>?>(null)
    private val countriesFlow = _countriesFlow.asStateFlow().filterNotNull().map { codes ->
        codes.map { Country(it) }
    }

    private val _suggestFlow = MutableStateFlow<List<String>?>(null)
    private val suggestFlow = _suggestFlow.asStateFlow().filterNotNull().map { codes ->
        codes.map { Country(it) }
    }

    private val countryFlow = settingsRepository.getNormalizeCountryFlow(api)

    private val _searchQueryFlow = MutableStateFlow("")
    private val searchQueryFlow = _searchQueryFlow.asSharedFlow()

    val uiItemsFlow = combine(
        countriesFlow,
        suggestFlow,
        countryFlow,
        searchQueryFlow
    ) { countries, suggest, selectedCountry, query ->
        if (query.isEmpty()) {
            defaultCountries(countries, suggest, selectedCountry)
        } else {
            searchCountries(countries, selectedCountry, query)
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _countriesFlow.value = loadCountries()
            _suggestFlow.value = loadSuggestions()
        }
    }

    private suspend fun searchCountries(countries: List<Country>, selectedCountry: String, query: String): List<Item> {
        val filteredList = countries.filter { it.contains(query) }
        val uiItems = mutableListOf<Item>()
        for ((index, data) in filteredList.withIndex()) {
            val position = ListCell.getPosition(filteredList.size, index)
            createItem(position, data, selectedCountry)?.let {
                uiItems.add(it)
            }
        }
        return uiItems
    }

    private suspend fun defaultCountries(countries: List<Country>, suggest: List<Country>, selectedCountry: String): List<Item> {
        val uiItems = mutableListOf<Item>()
        for ((index, data) in suggest.withIndex()) {
            val position = ListCell.getPosition(suggest.size, index)
            createItem(position, data, selectedCountry)?.let {
                uiItems.add(it)
            }
        }
        uiItems.add(Item.Space)

        for ((index, data) in countries.withIndex()) {
            val position = ListCell.getPosition(countries.size, index)
            createItem(position, data, selectedCountry)?.let {
                uiItems.add(it)
            }
        }
        return uiItems
    }

    fun search(query: CharSequence?) {
        if (query.isNullOrBlank()) {
            _searchQueryFlow.value = ""
        } else if (query.toString().equals("us", true) || query.toString().equals("usa", true)) {
            _searchQueryFlow.value = "United States"
        } else {
            _searchQueryFlow.value = query.toString()
        }
    }

    fun setCountry(country: String) {
        settingsRepository.country = country
    }

    private suspend fun createItem(
        position: ListCell.Position,
        data: Country,
        selectedCountry: String
    ): Item.Country? = withContext(Dispatchers.IO) {
        val selected: Boolean
        val code: String

        if (data.code.equals("auto", true)) {
            val apiCountry = api.resolveCountry() ?: settingsRepository.getLocale().country
            selected = selectedCountry.equals("auto", true)
            code = apiCountry
        } else {
            selected = data.code.equals(selectedCountry, ignoreCase = true)
            code = data.code
        }

        val iconRes = getFlagDrawable(code) ?: return@withContext null

        Item.Country(
            position = position,
            code = code,
            name = data.name,
            icon = iconRes,
            selected = selected
        )
    }

    private fun loadCountries(): List<String> {
        val list = mutableListOf<String>()
        Locale.getISOCountries().forEach {
            list.add(it)
        }
        list.add("NOKYC")
        return list.distinct()
    }

    private suspend fun loadSuggestions(): List<String> {
        val list = mutableListOf<String>()
        api.resolveCountry()?.let {
            list.add(it)
        }
        val country = settingsRepository.country
        if (country.isNotBlank()) {
            list.add(country)
        }
        val langCountry = settingsRepository.getLocale().country
        if (langCountry.isNotBlank()) {
            list.add(langCountry)
        }
        return list.distinct()
    }
}