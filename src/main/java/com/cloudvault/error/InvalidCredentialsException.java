package com.cloudvault.error;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("The email address or password is incorrect.");
    }
}
