package com.cloudvault;

import com.cloudvault.config.CloudVaultProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CloudVaultProperties.class)
public class CloudVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudVaultApplication.class, args);
    }
}
