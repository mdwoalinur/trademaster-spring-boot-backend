package com.trademaster.ims.exception;

import org.springframework.http.HttpStatus;

public class ApiResponseException extends RuntimeException {

    private final HttpStatus status;

    public ApiResponseException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ApiResponseException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
