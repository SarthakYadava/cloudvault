package com.cloudvault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AwsConfiguration {

    @Bean
    S3Client s3Client(CloudVaultProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.aws().region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(CloudVaultProperties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.aws().region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }
}
