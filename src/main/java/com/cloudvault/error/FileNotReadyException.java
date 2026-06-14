package com.cloudvault.error;

public class FileNotReadyException extends RuntimeException {

    public FileNotReadyException(String message) {
        super(message);
    }
}
