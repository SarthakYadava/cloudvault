package com.cloudvault.notification;

import com.cloudvault.config.CloudVaultProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "cloudvault.notifications",
        name = "delivery",
        havingValue = "smtp"
)
public class SmtpEmailDelivery implements EmailDelivery {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailDelivery(
            JavaMailSender mailSender,
            CloudVaultProperties properties
    ) {
        this.mailSender = mailSender;
        this.fromAddress = properties.notifications().fromAddress();
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromAddress);
        mail.setTo(message.recipient());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        mailSender.send(mail);
    }

    @Override
    public String mode() {
        return "SMTP";
    }
}
