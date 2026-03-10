package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R

class EthenaDetailsHolder(
    parent: ViewGroup,
): Holder<Item.EthenaDetails>(parent, R.layout.view_ethena_details) {

    private val apyView = findViewById<AppCompatTextView>(R.id.apy)
    private val apyTitleView = findViewById<AppCompatTextView>(R.id.apy_title)
    private val apySubtitleView = findViewById<AppCompatTextView>(R.id.apy_subtitle)
    private val bonusApyTitleView = findViewById<AppCompatTextView>(R.id.bonus_apy_title)
    private val bonusApySubtitleView = findViewById<AppCompatTextView>(R.id.bonus_apy_subtitle)
    private val faqView = findViewById<AppCompatTextView>(R.id.faq)
    private val checkEligibilityView = findViewById<AppCompatTextView>(R.id.check_eligibility)

    override fun onBind(item: Item.EthenaDetails) {
        apyTitleView.text = item.apyTitle
        apySubtitleView.text = item.apyDescription
        apyView.text = item.apyFormat

        bonusApyTitleView.text = item.bonusTitle
        bonusApySubtitleView.text = item.bonusDescription
        faqView.setOnClickListener {
            BrowserHelper.open(context, item.faqUrl)
        }
        checkEligibilityView.setOnClickListener {
            item.bonusUrl?.let {
                BrowserHelper.open(context, it)
            }
        }
    }

}