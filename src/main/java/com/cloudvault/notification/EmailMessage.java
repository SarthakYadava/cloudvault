package com.cloudvault.notification;

public record EmailMessage(
        String recipient,
        String subject,
        String body
) {
}
