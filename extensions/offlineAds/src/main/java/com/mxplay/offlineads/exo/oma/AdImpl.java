package com.mxplay.offlineads.exo.oma;

public class AdImpl implements Ad{
  private String adId;
  private boolean skippable;
  private double skipTimeOffset = -1.0D;
  private String title;
  private String description;
  private String advertiserName;
  private String mediaUrl;
  private long duration;
  private AdPodInfo adPodInfo = new AdPodInfoImpl();


  public AdImpl() {
  }

  public final String getAdId() {
    return this.adId;
  }

  public final void setAdId(String var1) {
    this.adId = var1;
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

  public final String getAdvertiserName() {
    return this.advertiserName;
  }

  public final void setAdvertiserName(String var1) {
    this.advertiserName = var1;
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

  @Override
  public String toString() {
    return "AdImpl{" +
        "adId='" + adId + '\'' +
        ", skippable=" + skippable +
        ", skipTimeOffset=" + skipTimeOffset +
        ", title='" + title + '\'' +
        ", description='" + description + '\'' +
        ", advertiserName='" + advertiserName + '\'' +
        ", mediaUrl='" + mediaUrl + '\'' +
        ", duration=" + duration +
        ", adPodInfo=" + adPodInfo +
        '}';
  }
}
