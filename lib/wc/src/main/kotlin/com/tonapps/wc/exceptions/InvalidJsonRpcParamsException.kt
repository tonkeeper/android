package com.tonapps.wc.exceptions

import java.lang.Exception

class InvalidJsonRpcParamsException(val requestId: String) : Exception("Invalid JSON RPC Request")
