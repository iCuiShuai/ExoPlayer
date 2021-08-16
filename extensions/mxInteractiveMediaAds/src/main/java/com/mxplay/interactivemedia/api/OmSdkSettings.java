package com.mxplay.interactivemedia.api;

public interface OmSdkSettings {
    int DEFAULT_MAX_REDIRECTS = 4;

    String getPpid();

    void setPpid(String var1);

    int getMaxRedirects();

    void setMaxRedirects(int var1);

    String getLanguage();

    void setLanguage(String var1);


    String getPlayerVersion();



    void setDebugMode(boolean var1);

    boolean isDebugMode();

}