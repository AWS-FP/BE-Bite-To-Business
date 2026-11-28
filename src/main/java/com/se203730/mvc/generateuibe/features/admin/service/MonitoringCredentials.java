package com.se203730.mvc.generateuibe.features.admin.service;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

final class MonitoringCredentials {
    private MonitoringCredentials() {}
    static AwsCredentialsProvider provider(String profile) {
        return profile == null || profile.isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : ProfileCredentialsProvider.create(profile.trim());
    }
}
