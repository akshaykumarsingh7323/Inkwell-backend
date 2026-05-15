package com.inkwell.media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Configuration
@Slf4j
public class S3Config {

    @Value("${aws.access-key}")
    private String accessKey;

    @Value("${aws.secret-key}")
    private String secretKey;

    @Value("${aws.region}")
    private String region;

    @jakarta.annotation.PostConstruct
    public void validateConfig() {
        if (accessKey == null || accessKey.isEmpty() || accessKey.equals("placeholder")) {
            log.warn("AWS Access Key is not configured correctly. S3 uploads may fail.");
        }
        if (secretKey == null || secretKey.isEmpty() || secretKey.equals("placeholder")) {
            log.warn("AWS Secret Key is not configured correctly. S3 uploads may fail.");
        }
        log.info("AWS S3 Configured for region: {}", region);
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .build();
    }
}
