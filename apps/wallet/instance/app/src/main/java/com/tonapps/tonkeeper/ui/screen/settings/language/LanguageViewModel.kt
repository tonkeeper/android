package com.tonapps.tonkeeper.ui.screen.settings.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.extensions.capitalized
import com.tonapps.tonkeeper.ui.screen.settings.language.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Language
import com.tonapps.wallet.localization.SupportedLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class LanguageViewModel(
    private val settingsRepository: SettingsRepository
): ViewModel() {

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filter { it.isNotEmpty() }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _uiItemsFlow.value = getItems()
        }
    }

    fun setLanguage(code: String) {
        settingsRepository.language = Language(code)

        _uiItemsFlow.value = _uiItemsFlow.value.map { item ->
            item.copy(selected = item.code == code)
        }

        val locale = if (code == Language.DEFAULT) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(code)
        }
        AppCompatDelegate.setApplicationLocales(locale)
    }

    private fun getItems(): List<Item> {
        val items = mutableListOf<Item>()
        for ((index, language) in SupportedLanguages.withIndex()) {
            val position = ListCell.getPosition(SupportedLanguages.size, index)
            items.add(getLangItem(language, position))
        }
        return items.toList()
    }

    private fun getLangItem(language: Language, position: ListCell.Position): Item {
        val checked = language == settingsRepository.language
        return Item(
            name = language.name.capitalized,
            nameLocalized = language.nameLocalized.capitalized,
            selected = checked,
            code = language.code,
            position = position
        )
    }
}