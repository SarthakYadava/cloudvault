package com.cloudvault.notification;

public interface EmailDelivery {

    void send(EmailMessage message);

    String mode();
}
