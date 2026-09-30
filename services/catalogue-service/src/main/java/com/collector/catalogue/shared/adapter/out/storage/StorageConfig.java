package com.collector.catalogue.shared.adapter.out.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Deux clients : l'URL pré-signée contient l'hôte, donc le presigner vise
 * l'adresse PUBLIQUE (celle que le navigateur ouvre) et le client HEAD l'adresse interne.
 */
@Configuration
class StorageConfig {

    @Bean
    S3Client s3Client(StorageProperties p) {
        return S3Client.builder()
                .endpointOverride(p.endpoint())
                .region(Region.of(p.region()))
                .credentialsProvider(credentials(p))
                .forcePathStyle(true)                // Garage et S3 auto-hébergés : style chemin
                .build();
    }

    @Bean
    S3Presigner s3Presigner(StorageProperties p) {
        return S3Presigner.builder()
                .endpointOverride(p.publicEndpoint())
                .region(Region.of(p.region()))
                .credentialsProvider(credentials(p))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static AwsCredentialsProvider credentials(StorageProperties p) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey(), p.secretKey()));
    }
}
