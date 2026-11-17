package org.example;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portal.admin-cleanup")
public record AdminCleanupProperties(boolean enabled,String url,String credentialsDirectory,
                                     String razorpayUrl,String razorpayCredentialsDirectory) {
    public String url(RunInput.PennyDropType type){return type==RunInput.PennyDropType.RAZORPAY?razorpayUrl:url;}
    public String credentialsDirectory(RunInput.PennyDropType type){return type==RunInput.PennyDropType.RAZORPAY?razorpayCredentialsDirectory:credentialsDirectory;}
}
