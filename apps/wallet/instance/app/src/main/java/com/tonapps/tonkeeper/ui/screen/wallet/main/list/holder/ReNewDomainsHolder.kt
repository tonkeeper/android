package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.extensions.locale
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.tonkeeper.ui.screen.dns.renew.DNSRenewScreen
import com.tonapps.tonkeeper.ui.screen.dns.renew.DNSRenewViewModel
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch

class ReNewDomainsHolder(
    parent: ViewGroup
): Holder<Item.RenewDomains>(parent, R.layout.view_wallet_renew_domains) {

    private val untilDateString: String by lazy {
        DateHelper.untilDate(
            locale = context.locale
        )
    }

    private val textView = findViewById<AppCompatTextView>(R.id.text)

    init {
        itemView.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
    }

    override fun onBind(item: Item.RenewDomains) {
        textView.text = context.getString(Localization.wallet_renew_dns, item.items.size, untilDateString)
        itemView.setOnClickListener {
            navigation?.add(DNSRenewScreen.newInstance(item.wallet, item.items))
        }
    }
}