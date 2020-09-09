package com.mxplay.offlineads.exo.oma;

public final class AdError extends Exception {
    private final AdError.AdErrorCode adErrorCode;
    private final AdError.AdErrorType adErrorType;

    public AdError(AdError.AdErrorType var1, AdError.AdErrorCode code, String message) {
        super(message);
        this.adErrorType = var1;
        this.adErrorCode = code;
    }

    public final AdError.AdErrorType getErrorType() {
        return this.adErrorType;
    }

    public final AdError.AdErrorCode getErrorCode() {
        return this.adErrorCode;
    }

    public final int getErrorCodeNumber() {
        return this.adErrorCode.getErrorNumber();
    }

    public final String getMessage() {
        return super.getMessage();
    }

    public final String toString() {
        String var1 = String.valueOf(this.adErrorType);
        String var2 = String.valueOf(this.adErrorCode);
        String var3 = this.getMessage();
        return (new StringBuilder(45 + String.valueOf(var1).length() + String.valueOf(var2).length() + String.valueOf(var3).length())).append("AdError [errorType: ").append(var1).append(", errorCode: ").append(var2).append(", message: ").append(var3).append("]").toString();
    }

    public static enum AdErrorCode {
        INTERNAL_ERROR(-1),
        VAST_MALFORMED_RESPONSE(100),
        UNKNOWN_AD_RESPONSE(1010),
        VAST_LOAD_TIMEOUT(301),
        VAST_TOO_MANY_REDIRECTS(302),
        VIDEO_PLAY_ERROR(400),
        VAST_MEDIA_LOAD_TIMEOUT(402),
        VAST_LINEAR_ASSET_MISMATCH(403),
        OVERLAY_AD_PLAYING_FAILED(500),
        OVERLAY_AD_LOADING_FAILED(502),
        VAST_NONLINEAR_ASSET_MISMATCH(503),
        COMPANION_AD_LOADING_FAILED(603),
        UNKNOWN_ERROR(900),
        VAST_EMPTY_RESPONSE(1009),
        FAILED_TO_REQUEST_ADS(1005),
        VAST_ASSET_NOT_FOUND(1007),
        ADS_REQUEST_NETWORK_ERROR(1012),
        INVALID_ARGUMENTS(1101),
        PLAYLIST_NO_CONTENT_TRACKING(1205);

        private final int a;

        private AdErrorCode(int var3) {
            this.a = var3;
        }

        public final int getErrorNumber() {
            return this.a;
        }

        public final boolean equals(int var1) {
            return this.a == var1;
        }

        public final String toString() {
            String var1 = this.name();
            int var2 = this.a;
            return (new StringBuilder(41 + String.valueOf(var1).length())).append("AdErrorCode [name: ").append(var1).append(", number: ").append(var2).append("]").toString();
        }
    }

    public static enum AdErrorType {
        LOAD,
        PLAY;

        private AdErrorType() {
        }
    }
}