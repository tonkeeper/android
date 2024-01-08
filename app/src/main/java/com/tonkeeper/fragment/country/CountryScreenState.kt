package com.tonkeeper.fragment.country

import com.tonkeeper.App
import com.tonkeeper.fragment.country.list.CountryItem
import uikit.list.ListCell
import uikit.mvi.UiState

data class CountryScreenState(
    val selectedCountry: String = App.settings.country,
    val countries: List<CountryScreenFeature.Country> = emptyList(),
): UiState() {

    val items: List<CountryItem>
        get() {
            val items = mutableListOf<CountryItem>()
            for ((index, country) in countries.withIndex()) {
                val position = ListCell.getPosition(countries.size, index)
                val selected = country.code == selectedCountry
                items.add(CountryItem(country, selected, position))
            }
            return items
        }
}