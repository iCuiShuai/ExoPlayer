package com.mxplay.offlineads.exo.vast.model;

import java.util.ArrayList;
import java.util.List;

public class VMAPModel {

  private List<AdBreak> adBreaks = new ArrayList<>();

  public void addABreak(AdBreak adBreak) {
    adBreaks.add(adBreak);
  }

  public List<AdBreak> getAdBreaks() {
    return adBreaks;
  }
}
