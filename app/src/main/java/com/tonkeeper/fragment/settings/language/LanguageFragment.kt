package com.tonkeeper.fragment.settings.language

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeperx.R
import com.tonkeeper.App
import com.tonkeeper.core.language.LANGUAGE_DEFAULT
import com.tonkeeper.extensions.capitalized
import com.tonkeeper.fragment.settings.language.list.LanguageAdapter
import com.tonkeeper.fragment.settings.language.list.LanguageItem
import uikit.base.BaseFragment
import uikit.list.LinearLayoutManager
import uikit.list.ListCell
import uikit.widget.BackHeaderView
import java.util.Locale

class LanguageFragment: BaseFragment(R.layout.fragment_list_picker), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = LanguageFragment()

        private val supportedLanguages = listOf(LANGUAGE_DEFAULT, "en", "ru")
    }

    override var doOnDragging: ((Boolean) -> Unit)? = null
    override var doOnDraggingProgress: ((Float) -> Unit)? = null

    private val adapter = LanguageAdapter {
        setAppLanguage(it.code)
    }

    private lateinit var headerView: BackHeaderView
    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnBackClick = { finish() }
        headerView.setTitle(getString(R.string.language))

        listView = view.findViewById(R.id.list)
        listView.layoutManager = LinearLayoutManager(view.context)
        listView.adapter = adapter

        adapter.submitList(getItems())
    }

    private fun setAppLanguage(code: String) {
        App.settings.language = code

        val locale = if (code == LANGUAGE_DEFAULT) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(code)
        }
        AppCompatDelegate.setApplicationLocales(locale)
    }

    private fun getItems(): List<LanguageItem> {
        val items = mutableListOf<LanguageItem>()
        for ((index, code) in supportedLanguages.withIndex()) {
            val checked = isCurrentLanguage(code)
            val position = ListCell.getPosition(supportedLanguages.size, index)
            if (code == LANGUAGE_DEFAULT) {
                items.add(LanguageItem(name = getString(R.string.system), selected = checked, code = LANGUAGE_DEFAULT, position = position))
            } else {
                items.add(getLangItem(code, checked, position))
            }

        }
        return items
    }

    private fun getLangItem(code: String, checked: Boolean, position: ListCell.Position): LanguageItem {
        val locale = Locale(code)
        val name = locale.displayLanguage.capitalized
        val nameLocalized = locale.getDisplayLanguage(locale).capitalized
        return LanguageItem(name = name, nameLocalized = nameLocalized, selected = checked, code = code, position = position)
    }

    private fun isCurrentLanguage(code: String): Boolean {
        return App.settings.language == code
    }
}