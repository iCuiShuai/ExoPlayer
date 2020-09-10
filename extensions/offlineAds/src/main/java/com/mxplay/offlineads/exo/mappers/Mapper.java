package com.mxplay.offlineads.exo.mappers;

import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;
import com.google.android.exoplayer2.C;
import com.mxplay.offlineads.exo.oma.Ad;
import com.mxplay.offlineads.exo.oma.AdGroup;
import com.mxplay.offlineads.exo.oma.AdImpl;
import com.mxplay.offlineads.exo.oma.AdPodInfoImpl;
import com.mxplay.offlineads.exo.vast.model.AdBreak;
import com.mxplay.offlineads.exo.vast.model.VastAdModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Mapper {

  private static final int PRE_ROLL_POD_INDEX = 0;
  private static final int POST_ROLL_POD_INDEX = -1;


  public List<AdGroup> toAdGroups(List<AdBreak> adBreaks) throws Exception {
    Set<AdGroup> ads = new HashSet<>();
    int podIndex = 1;
    for (int i=0;i< adBreaks.size();i++) {
      AdBreak adBreak = adBreaks.get(i);
      AdGroup adGroup = toAdGroup(adBreak, podIndex);
      if (adGroup != null && !adGroup.getAds().isEmpty()) {
        if (adBreak.getPodIndex() > 0)podIndex++;
        ads.add(adGroup);
      }
    }
    return new ArrayList<>(ads);
  }




  private AdGroup toAdGroup(AdBreak adBreak, int index) throws Exception {
    List<Ad> ads = new ArrayList<>();
    List<VastAdModel> adsList = adBreak.getVastModel().getAdsList();
    AdPodInfoImpl adPodInfo = new AdPodInfoImpl();
    if (adBreak.getStartTime() == C.INDEX_UNSET){ // postroll
      adPodInfo.podIndex = POST_ROLL_POD_INDEX;
    }else if (adBreak.getStartTime() == 0L){ // preroll
      adPodInfo.podIndex = PRE_ROLL_POD_INDEX;
    }else { // midroll
      adPodInfo.podIndex = index;
    }
    adBreak.setPodIndex(adPodInfo.podIndex);
    adPodInfo.timeOffset = adBreak.getStartTime();
    adPodInfo.totalAds = adsList.size();
    for (int i = 1; i <= adsList.size(); i++) {
      VastAdModel vastAdModel = adsList.get(i-1);
      AdImpl ad = new AdImpl();
      ad.setAdPodInfo(new AdPodInfoImpl(adPodInfo, i));
      ad.setAdId(vastAdModel.getAdId());
      ad.setAdvertiserName(vastAdModel.getAdvertiserName());
      ad.setSkippable(vastAdModel.isSkippable());
      ad.setSkipTimeOffset(vastAdModel.getSkipTimeOffset());
      ad.setTitle(vastAdModel.getTitle());
      ad.setDescription(vastAdModel.getDescription());
      ad.setDuration(vastAdModel.getDuration());
      ad.setMediaUrl(vastAdModel.getMediaUrl());
      if (TextUtils.isEmpty(ad.getAdId())){
        Log.w("Mapper", "Invalid ad id skipping ad group ");
        return null;
      }
      if (TextUtils.isEmpty(ad.getMediaUrl()) || URLUtil.isNetworkUrl(ad.getMediaUrl())){
        Log.w("Mapper", "Invalid ad media file skipping ad group "+ ad.getMediaUrl());
        return null;
      }
      ads.add(ad);
    }
    return new AdGroup(adPodInfo.timeOffset, ads);
  }

}
