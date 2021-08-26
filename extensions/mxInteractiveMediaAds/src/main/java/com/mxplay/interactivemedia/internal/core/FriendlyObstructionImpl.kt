package com.mxplay.interactivemedia.internal.core

import android.view.View
import com.mxplay.interactivemedia.api.FriendlyObstruction
import com.mxplay.interactivemedia.api.FriendlyObstructionPurpose

class FriendlyObstructionImpl(val _view : View, val _purpose : FriendlyObstructionPurpose, val _detailReason : String? ) : FriendlyObstruction {

    override fun getView(): View {
        return _view
    }

    override fun getPurpose(): FriendlyObstructionPurpose {
        return _purpose
    }

    override fun getDetailedReason(): String? {
        return _detailReason
    }
}