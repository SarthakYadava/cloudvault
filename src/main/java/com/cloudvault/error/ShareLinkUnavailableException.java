package com.cloudvault.error;

import org.springframework.http.HttpStatus;

public class ShareLinkUnavailableException extends RuntimeException {

    private final HttpStatus status;

    public ShareLinkUnavailableException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
