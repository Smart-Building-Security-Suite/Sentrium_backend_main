package com.securitysuite.backend.pushnotification;

import io.github.hlspablo.exposdkjava.ExpoPushNotificationClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExpoPushNotificationConfig {

    @Value("${expo.access-token:}")
    private String expoAccessToken;

    @Bean
    public ExpoPushNotificationClient expoPushNotificationClient() {
        CloseableHttpClient httpClient = HttpClients.createDefault();

        ExpoPushNotificationClient.Builder builder = ExpoPushNotificationClient.builder()
                .setHttpClient(httpClient);

        // Add access token if configured (for Expo's push notification service)
        if (expoAccessToken != null && !expoAccessToken.isBlank()) {
            builder.setAccessToken(expoAccessToken);
        }

        return builder.build();
    }
}
