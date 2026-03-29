package com.wallet.exception;

import org.springframework.http.HttpStatus;

public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }

    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}