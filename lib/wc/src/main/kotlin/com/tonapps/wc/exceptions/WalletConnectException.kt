package com.tonapps.wc.exceptions

import com.tonapps.wc.models.WcError

class WalletConnectException(wcError: WcError) : RuntimeException(wcError.message)
