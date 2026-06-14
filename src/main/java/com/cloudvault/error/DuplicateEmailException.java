package com.cloudvault.error;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("An account with that email address already exists.");
    }
}
