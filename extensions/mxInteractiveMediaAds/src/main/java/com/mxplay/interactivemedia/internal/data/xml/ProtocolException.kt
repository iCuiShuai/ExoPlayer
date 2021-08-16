package com.mxplay.interactivemedia.internal.data.xml

import com.mxplay.interactivemedia.api.AdError

class ProtocolException(val error: AdError, cause : Throwable? = null) : Exception(error.message, cause)