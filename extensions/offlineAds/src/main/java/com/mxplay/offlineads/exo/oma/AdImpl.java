package com.mxplay.offlineads.exo.oma;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class AdImpl implements Ad{
  private String adId;
  private String creativeId;
  private String creativeAdId;
  private String universalAdIdValue;
  private String universalAdIdRegistry;
  private String adSystem;
  private boolean linear;
  private boolean skippable;
  private double skipTimeOffset = -1.0D;
  private boolean disableUi;
  private String title;
  private String description;
  private String contentType;
  private String advertiserName;
  private String surveyUrl;
  private String dealId;
  private int width;
  private int height;
  private int vastMediaBitrate;
  private int vastMediaHeight;
  private int vastMediaWidth;
  private String traffickingParameters;
  private String clickThroughUrl;
  private String mediaUrl;
  private long duration;
  private AdPodInfo adPodInfo = new AdPodInfoImpl();

  private String[] adWrapperIds;

  private String[] adWrapperSystems;

  private String[] adWrapperCreativeIds;
  private boolean isUiDisabled_ = false;

  public AdImpl() {
  }

  public final String getAdId() {
    return this.adId;
  }

  public final void setAdId(String var1) {
    this.adId = var1;
  }

  public final String getCreativeId() {
    return this.creativeId;
  }

  public final void setCreativeId(String var1) {
    this.creativeId = var1;
  }

  public final String getCreativeAdId() {
    return this.creativeAdId;
  }

  public final void setCreativeAdId(String var1) {
    this.creativeAdId = var1;
  }

  public final String getUniversalAdIdValue() {
    return this.universalAdIdValue;
  }

  public final void setUniversalAdIdValue(String var1) {
    this.universalAdIdValue = var1;
  }

  public final String getUniversalAdIdRegistry() {
    return this.universalAdIdRegistry;
  }

  public final void setUniversalAdIdRegistry(String var1) {
    this.universalAdIdRegistry = var1;
  }

  public final String getAdSystem() {
    return this.adSystem;
  }

  public final void setAdSystem(String var1) {
    this.adSystem = var1;
  }

  public final String[] getAdWrapperIds() {
    return this.adWrapperIds;
  }

  public final void setAdWrapperIds(String[] var1) {
    this.adWrapperIds = var1;
  }

  public final String[] getAdWrapperSystems() {
    return this.adWrapperSystems;
  }

  public final void setAdWrapperSystems(String[] var1) {
    this.adWrapperSystems = var1;
  }

  public final String[] getAdWrapperCreativeIds() {
    return this.adWrapperCreativeIds;
  }

  public final void setAdWrapperCreativeIds(String[] var1) {
    this.adWrapperCreativeIds = var1;
  }

  public final boolean isLinear() {
    return this.linear;
  }

  public final void setLinear(boolean var1) {
    this.linear = var1;
  }

  public final boolean isSkippable() {
    return this.skippable;
  }

  public final void setSkippable(boolean var1) {
    this.skippable = var1;
  }

  public final double getSkipTimeOffset() {
    return this.skipTimeOffset;
  }

  public final void setSkipTimeOffset(double var1) {
    this.skipTimeOffset = var1;
  }

  public final boolean isUiDisabled() {
    return this.isUiDisabled_;
  }

  public final void setUiDisabled(boolean var1) {
    this.isUiDisabled_ = var1;
  }

  public final boolean canDisableUi() {
    return this.disableUi;
  }

  public final void setCanDisableUi(boolean var1) {
    this.disableUi = var1;
  }

  public final String getTitle() {
    return this.title;
  }

  public final void setTitle(String var1) {
    this.title = var1;
  }

  public final String getDescription() {
    return this.description;
  }

  public final void setDescription(String var1) {
    this.description = var1;
  }

  public final String getContentType() {
    return this.contentType;
  }

  public final void setContentType(String var1) {
    this.contentType = var1;
  }

  public final String getAdvertiserName() {
    return this.advertiserName;
  }

  public final void setAdvertiserName(String var1) {
    this.advertiserName = var1;
  }

  public final String getSurveyUrl() {
    return this.surveyUrl;
  }

  public final void setSurveyUrl(String var1) {
    this.surveyUrl = var1;
  }

  public final String getDealId() {
    return this.dealId;
  }

  public final void setDealId(String var1) {
    this.dealId = var1;
  }

  public final int getWidth() {
    return this.width;
  }

  public final void setWidth(int var1) {
    this.width = var1;
  }

  public final int getHeight() {
    return this.height;
  }

  public final void setHeight(int var1) {
    this.height = var1;
  }

  public final int getVastMediaWidth() {
    return this.vastMediaWidth;
  }

  public final void setVastMediaWidth(int var1) {
    this.vastMediaWidth = var1;
  }

  public final int getVastMediaHeight() {
    return this.vastMediaHeight;
  }

  public final void setVastMediaHeight(int var1) {
    this.vastMediaHeight = var1;
  }

  public final int getVastMediaBitrate() {
    return this.vastMediaBitrate;
  }

  public final void setVastMediaBitrate(int var1) {
    this.vastMediaBitrate = var1;
  }

  public final String getTraffickingParameters() {
    return this.traffickingParameters;
  }

  public final void setTraffickingParameters(String var1) {
    this.traffickingParameters = var1;
  }

  public final String getClickThruUrl() {
    return this.clickThroughUrl;
  }

  public final void setClickThruUrl(String var1) {
    this.clickThroughUrl = var1;
  }

  public final void setMediaUrl(String mediaUrl) {
    this.mediaUrl = mediaUrl;
  }

  public String getMediaUrl() {
    return mediaUrl;
  }

  public final long getDuration() {
    return this.duration;
  }

  public final void setDuration(long var1) {
    this.duration = var1;
  }

  public final AdPodInfo getAdPodInfo() {
    return this.adPodInfo;
  }

  public final void setAdPodInfo(AdPodInfo var1) {
    this.adPodInfo = var1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AdImpl)) {
      return false;
    }
    AdImpl ad = (AdImpl) o;
    return adId.equals(ad.adId) &&
        mediaUrl.equals(ad.mediaUrl);
  }


  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (adId != null ? adId.hashCode() :0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result +  adPodInfo.hashCode();

    return result;
  }


  public final String toString() {
    String var1 = this.adId;
    String var2 = this.creativeId;
    String var3 = this.creativeAdId;
    String var4 = this.universalAdIdValue;
    String var5 = this.universalAdIdRegistry;
    String var6 = this.title;
    String var7 = this.description;
    String var8 = this.contentType;
    String var9 = Arrays.toString(this.adWrapperIds);
    String var10 = Arrays.toString(this.adWrapperSystems);
    String var11 = Arrays.toString(this.adWrapperCreativeIds);
    String var12 = this.adSystem;
    String var13 = this.advertiserName;
    String var14 = this.surveyUrl;
    String var15 = this.dealId;
    boolean var16 = this.linear;
    boolean var17 = this.skippable;
    int var18 = this.width;
    int var19 = this.height;
    String var20 = this.traffickingParameters;
    String var21 = this.clickThroughUrl;
    double var22 = this.duration;
    String var24 = String.valueOf(this.adPodInfo);
    boolean var26 = this.disableUi;
    double var27 = this.skipTimeOffset;
    return (new StringBuilder(455 + String.valueOf(var1).length() + String.valueOf(var2).length() + String.valueOf(var3).length() + String.valueOf(var4).length() + String.valueOf(var5).length() + String.valueOf(var6).length() + String.valueOf(var7).length() + String.valueOf(var8).length() + String.valueOf(var9).length() + String.valueOf(var10).length() + String.valueOf(var11).length() + String.valueOf(var12).length() + String.valueOf(var13).length() + String.valueOf(var14).length() + String.valueOf(var15).length() + String.valueOf(var20).length() + String.valueOf(var21).length() + String.valueOf(var24).length()).append("Ad [adId=").append(var1).append(", creativeId=").append(var2).append(", creativeAdId=").append(var3).append(", universalAdIdValue=").append(var4).append(", universalAdIdRegistry=").append(var5).append(", title=").append(var6).append(", description=").append(var7).append(", contentType=").append(var8).append(", adWrapperIds=").append(var9).append(", adWrapperSystems=").append(var10).append(", adWrapperCreativeIds=").append(var11).append(", adSystem=").append(var12).append(", advertiserName=").append(var13).append(", surveyUrl=").append(var14).append(", dealId=").append(var15).append(", linear=").append(var16).append(", skippable=").append(var17).append(", width=").append(var18).append(", height=").append(var19).append(", traffickingParameters=").append(var20).append(", clickThroughUrl=").append(var21).append(", duration=").append(var22).append(", adPodInfo=").append(var24).append(", uiElements=").append(", disableUi=").append(var26).append(", MediaUrl = ").append(mediaUrl).append(", skipTimeOffset=").append(var27).append("]").toString());
  }


}
