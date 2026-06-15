package com.cloudvault.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "cloudvault.notifications",
        name = "delivery",
        havingValue = "log",
        matchIfMissing = true
)
public class LoggingEmailDelivery implements EmailDelivery {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailDelivery.class);

    @Override
    public void send(EmailMessage message) {
        log.info(
                "CloudVault email [to={}, subject={}]\n{}",
                message.recipient(),
                message.subject(),
                message.body()
        );
    }

    @Override
    public String mode() {
        return "LOG";
    }
}
