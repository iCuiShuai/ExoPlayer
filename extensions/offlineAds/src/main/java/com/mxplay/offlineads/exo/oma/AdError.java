package com.mxplay.offlineads.exo.oma;

public final class AdError extends Exception {
    private final AdError.AdErrorCode adErrorCode;
    private final AdError.AdErrorType adErrorType;

    public AdError(AdError.AdErrorType adErrorType, AdError.AdErrorCode code, String message) {
        super(message);
        this.adErrorType = adErrorType;
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
        VAST_LINEAR_ASSET_MISMATCH(403),
        UNKNOWN_ERROR(900),
        VAST_EMPTY_RESPONSE(1009),
        FAILED_TO_REQUEST_ADS(1005);

        private final int code;

        private AdErrorCode(int code) {
            this.code = code;
        }

        public final int getErrorNumber() {
            return this.code;
        }

        public final boolean equals(int code) {
            return this.code == code;
        }

        @Override
        public String toString() {
            return "AdErrorCode{" +
                "a=" + code +
                '}';
        }
    }

    public static enum AdErrorType {
        LOAD,
        PLAY;
        private AdErrorType() {
        }
    }
}