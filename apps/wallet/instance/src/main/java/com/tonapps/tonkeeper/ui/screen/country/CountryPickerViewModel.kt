package com.tonapps.tonkeeper.ui.screen.country

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.locale
import com.tonapps.tonkeeper.extensions.countryEmoji
import com.tonapps.tonkeeper.extensions.countryName
import com.tonapps.tonkeeper.ui.screen.country.list.Item
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
import uikit.extensions.context
import java.util.Locale

class CountryPickerViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val api: API,
): AndroidViewModel(app) {

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

    private val _searchQueryFlow = MutableStateFlow("")
    private val searchQueryFlow = _searchQueryFlow.asSharedFlow()

    val uiItemsFlow = combine(
        countriesFlow,
        suggestFlow,
        settingsRepository.countryFlow,
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

    private fun searchCountries(countries: List<Country>, selectedCountry: String, query: String): List<Item> {
        val filteredList = countries.filter { it.contains(query) }
        val uiItems = mutableListOf<Item>()
        for ((index, data) in filteredList.withIndex()) {
            val position = ListCell.getPosition(filteredList.size, index)
            uiItems.add(createItem(position, data, selectedCountry))
        }
        return uiItems
    }

    private fun defaultCountries(countries: List<Country>, suggest: List<Country>, selectedCountry: String): List<Item> {
        val uiItems = mutableListOf<Item>()
        for ((index, data) in suggest.withIndex()) {
            val position = ListCell.getPosition(suggest.size, index)
            uiItems.add(createItem(position, data, selectedCountry))
        }
        uiItems.add(Item.Space)

        for ((index, data) in countries.withIndex()) {
            val position = ListCell.getPosition(countries.size, index)
            uiItems.add(createItem(position, data, selectedCountry))
        }
        return uiItems
    }

    fun search(query: CharSequence?) {
        _searchQueryFlow.value = query.toString()
    }

    fun setCountry(country: String) {
        settingsRepository.country = country
    }

    private fun createItem(
        position: ListCell.Position,
        data: Country,
        selectedCountry: String
    ): Item.Country {
        val selected = data.code.equals(selectedCountry, ignoreCase = true)
        return Item.Country(
            position,
            data.code,
            data.name,
            data.emoji,
            selected
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
        list.add(settingsRepository.country)
        list.add(context.locale.country)
        return list.distinct()
    }
}