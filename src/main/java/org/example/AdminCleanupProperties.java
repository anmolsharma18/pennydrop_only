package org.example;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portal.admin-cleanup")
public record AdminCleanupProperties(boolean enabled,String url,String credentialsDirectory) {}
