package com.tonkeeper.fragment.collectibles

import androidx.fragment.app.viewModels
import com.tonkeeper.R
import com.tonkeeper.fragment.main.MainTabScreen

class CollectiblesScreen: MainTabScreen<CollectiblesScreenState, CollectiblesScreenEffect, CollectiblesScreenFeature>(R.layout.fragment_collectibles) {

    companion object {
        fun newInstance() = CollectiblesScreen()
    }

    override val feature: CollectiblesScreenFeature by viewModels()


    override fun onUpScroll() {

    }


    override fun newUiState(state: CollectiblesScreenState) {

    }

}