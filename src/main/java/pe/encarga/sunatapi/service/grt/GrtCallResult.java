package pe.encarga.sunatapi.service.grt;

public class GrtCallResult {

    private final boolean success;
    private final ResponseGeneraXml response;
    private final String errorCode;
    private final String errorMessage;

    private GrtCallResult(boolean success, ResponseGeneraXml response, String errorCode, String errorMessage) {
        this.success = success;
        this.response = response;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static GrtCallResult ok(ResponseGeneraXml response) {
        return new GrtCallResult(true, response, null, null);
    }

    public static GrtCallResult error(String errorCode, String errorMessage) {
        return new GrtCallResult(false, null, errorCode, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public ResponseGeneraXml getResponse() {
        return response;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}