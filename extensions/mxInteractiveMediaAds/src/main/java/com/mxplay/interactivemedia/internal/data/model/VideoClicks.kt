//
//  VideoClicks.java
//
//  Copyright (c) 2014 Nexage. All rights reserved.
//
package com.mxplay.interactivemedia.internal.data.model

class VideoClicks {
    var clickThrough: String? = null
    var clickTracking: ClickEvent? = null

   companion object{
       const val TAG_CLICK_THROUGH = "ClickThrough"
   }
}