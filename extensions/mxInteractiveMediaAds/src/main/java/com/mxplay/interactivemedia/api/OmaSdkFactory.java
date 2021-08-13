//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.mxplay.interactivemedia.api;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.mxplay.interactivemedia.internal.core.CompanionAdSlotImpl;
import com.mxplay.interactivemedia.internal.core.FriendlyObstructionImpl;
import com.mxplay.interactivemedia.internal.core.OmSdkSettingsImpl;
import com.mxplay.mediaads.exo.OmaUtil;
import com.mxplay.interactivemedia.internal.core.AdLoaderImpl;
import com.mxplay.interactivemedia.api.player.VideoAdPlayer;
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper;

public class OmaSdkFactory  {


    private static OmaSdkFactory instance;


    private OmaSdkFactory() {
    }

    public static OmaSdkFactory getInstance() {
        if (instance == null) {
            instance = new OmaSdkFactory();
        }
        return instance;
    }


    public OmSdkSettings createImaSdkSettings() {
        return new OmSdkSettingsImpl();
    }



    public AdsLoader createAdsLoader(Context context, Configuration configuration, OmaUtil utils, OmSdkSettings sdkSettings, AdDisplayContainer adDisplayContainer) {
        return new AdLoaderImpl(context, configuration, utils, sdkSettings, adDisplayContainer,
            XmlParserHelper.INSTANCE);
    }

  
    public static AdDisplayContainer createAdDisplayContainer(ViewGroup  viewGroup, VideoAdPlayer  videoAdPlayer) {
        AdDisplayContainer adDisplayContainer = new AdDisplayContainer();
        adDisplayContainer.setAdContainer(viewGroup);
        adDisplayContainer.setPlayer(videoAdPlayer);
        return adDisplayContainer;
    }

   
    public static AdDisplayContainer createAudioAdDisplayContainer(Context context, VideoAdPlayer videoAdPlayer) {
        AdDisplayContainer adDisplayContainer = new AdDisplayContainer();
        adDisplayContainer.setPlayer(videoAdPlayer);
        return adDisplayContainer;
    }

   

    public @NonNull AdsRenderingSettings createAdsRenderingSettings() {
        return new AdsRenderingSettings();
    }

    public AdsRequest createAdsRequest() {
        return new AdsRequest();
    }

   
    public CompanionAdSlot createCompanionAdSlot() {
        return new CompanionAdSlotImpl();
    }

    public FriendlyObstruction createFriendlyObstruction(View view, FriendlyObstructionPurpose friendlyObstructionPurpose, @Nullable String detailReason) {
        return new FriendlyObstructionImpl(view, friendlyObstructionPurpose, detailReason);
    }

    
}
