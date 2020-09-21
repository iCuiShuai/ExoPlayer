package com.mxplay.offlineads.exo.mappers;

import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;

import com.mxplay.offlineads.exo.oma.AdGroup;
import com.mxplay.offlineads.exo.oma.AdImpl;
import com.mxplay.offlineads.exo.oma.AdPodInfoImpl;
import com.mxplay.offlineads.exo.vast.model.AdBreak;
import com.mxplay.offlineads.exo.vast.model.VastAdModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Mapper {




  public List<AdGroup> toAdGroups(List<AdBreak> adBreaks) throws Exception {
    List<AdGroup> ads = new ArrayList<>();
    for (int i=0;i< adBreaks.size();i++) {
      AdBreak adBreak = adBreaks.get(i);
      AdGroup adGroup = toAdGroup(adBreak);
      if (adGroup != null && !adGroup.getAds().isEmpty()) {
        int index = ads.indexOf(adGroup);
        if (index >= 0){
          ads.get(index).getAds().addAll(adGroup.getAds());
        }else {
          ads.add(adGroup);
        }
      }
    }
    return new ArrayList<>(ads);
  }




  private AdGroup toAdGroup(AdBreak adBreak) throws Exception {
    List<AdImpl> ads = new ArrayList<>();
    List<VastAdModel> adsList = adBreak.getVastModel().getAdsList();
    for (int i = 1; i <= adsList.size(); i++) {
      VastAdModel vastAdModel = adsList.get(i-1);
      AdImpl ad = new AdImpl();
      ad.setAdPodInfo(new AdPodInfoImpl());
      ad.setAdId(vastAdModel.getAdId());
      ad.setAdvertiserName(vastAdModel.getAdvertiserName());
      double skipTimeOffset = vastAdModel.getSkipTimeOffset();
      ad.setSkippable(skipTimeOffset > -1);
      ad.setSkipTimeOffset(skipTimeOffset);
      ad.setTitle(vastAdModel.getTitle());
      ad.setDescription(vastAdModel.getDescription());
      ad.setDuration(vastAdModel.getDuration());
      ad.setMediaUrl(vastAdModel.getMediaUrl());
      if (TextUtils.isEmpty(ad.getAdId())){
        Log.w("Mapper", "Invalid ad id skipping ad group ");
        return null;
      }
      if (TextUtils.isEmpty(ad.getMediaUrl()) || URLUtil.isNetworkUrl(ad.getMediaUrl()) || !(new File(ad.getMediaUrl()).exists())){
        Log.w("Mapper", "Invalid ad media file skipping ad group "+ ad.getMediaUrl());
        return null;
      }
      ads.add(ad);
    }
    return new AdGroup(adBreak.getStartTime(), ads);
  }

}
