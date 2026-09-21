package com.tonapps.wc.exceptions

import java.lang.Exception

class InvalidSessionException(val topic: String?) : Exception("Invalid session")