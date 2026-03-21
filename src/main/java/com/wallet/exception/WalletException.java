package com.wallet.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class WalletException extends RuntimeException {

    private final HttpStatus status;

    public WalletException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}