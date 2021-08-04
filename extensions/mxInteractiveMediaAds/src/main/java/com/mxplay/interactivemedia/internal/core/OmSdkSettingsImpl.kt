package com.mxplay.interactivemedia.internal.core

import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.mxplay.interactivemedia.api.OmSdkSettings


class OmSdkSettingsImpl : OmSdkSettings {

    private var _ppId: String? = null
    private var maxRedirectAllowed: Int = OmSdkSettings.DEFAULT_MAX_REDIRECTS
    private var _language: String? = null
    private var debug = false

    // mvp
    private var _playerVersion: String? = ExoPlayerLibraryInfo.VERSION

    override fun getPpid(): String {
        return _ppId ?: ""
    }

    override fun setPpid(ppid: String?) {
        _ppId = ppid
    }

    override fun getMaxRedirects(): Int {
        return maxRedirectAllowed
    }

    override fun setMaxRedirects(maxRedirectAllowed: Int) {
        this.maxRedirectAllowed = maxRedirectAllowed.coerceAtLeast(1)
    }

    override fun getLanguage(): String {
        return _language ?: "en"
    }

    override fun setLanguage(language: String?) {
        _language = language
    }


    override fun getPlayerVersion(): String {
        return _playerVersion ?: ""
    }


    override fun setDebugMode(debug: Boolean) {
        this.debug = debug
    }

    override fun isDebugMode(): Boolean {
        return debug
    }
}