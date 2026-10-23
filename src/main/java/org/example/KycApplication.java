package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({PortalSettings.class,AdminCleanupProperties.class})
@EnableScheduling
public class KycApplication {
    public static void main(String[] args) { SpringApplication.run(KycApplication.class, args); }
}
