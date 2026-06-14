package com.cloudvault.error;

import java.util.UUID;

public class FileNotFoundException extends RuntimeException {

    public FileNotFoundException(UUID id) {
        super("No file exists with id " + id + ".");
    }
}
