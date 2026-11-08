package com.venuenexus.resource;

import com.venuenexus.resource.qr.QrTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QrConfig {
    @Bean
    public QrTokenService qrTokenService() {
        return new QrTokenService();
    }
}
