package com.example.agentforge.model;

 

public class ModelException extends RuntimeException {

    private final String provider;
    private final int statusCode;
    private final String responseBody;

    public ModelException(String message) {
        this(message, null, 0, null, null);
    }

    public ModelException(String message, Throwable cause) {
        this(message, null, 0, null, cause);
    }

    public ModelException(String message, String provider, int statusCode, String responseBody) {
        this(message, provider, statusCode, responseBody, null);
    }

    public ModelException(String message, String provider, int statusCode, String responseBody, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public String getProvider() {
        return provider;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isRetryable() {
        return statusCode == 429 || statusCode >= 500 || statusCode == 0;
    }
}
