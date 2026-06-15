package com.cloudvault.error;

public class WorkspaceNotFoundException extends RuntimeException {

    public WorkspaceNotFoundException() {
        super("The workspace or document request was not found.");
    }
}
