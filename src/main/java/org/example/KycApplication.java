package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({PortalSettings.class,AdminCleanupProperties.class})
@EnableScheduling
public class KycApplication {
    static { if(System.getProperty("java.net.preferIPv4Stack")==null)System.setProperty("java.net.preferIPv4Stack","true"); }
    public static void main(String[] args) { SpringApplication.run(KycApplication.class, args); }
}
