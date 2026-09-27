package pe.encarga.sunatapi.service;

public class CdrParseResult {

    private boolean success;
    private String responseCode;
    private String description;
    private String errorCode;
    private String errorMessage;

    public static CdrParseResult ok(String responseCode, String description) {
        CdrParseResult r = new CdrParseResult();
        r.success = true;
        r.responseCode = responseCode;
        r.description = description;
        return r;
    }

    public static CdrParseResult rejected(String responseCode, String description) {
        CdrParseResult r = new CdrParseResult();
        r.success = false;
        r.responseCode = responseCode;
        r.description = description;
        r.errorCode = "SUNAT_REJECTED";
        r.errorMessage = description;
        return r;
    }

    public static CdrParseResult error(String errorCode, String errorMessage) {
        CdrParseResult r = new CdrParseResult();
        r.success = false;
        r.errorCode = errorCode;
        r.errorMessage = errorMessage;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}