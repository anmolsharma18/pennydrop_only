package org.example;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portal")
public record PortalSettings(String chromeVersion, int waitSeconds, int settleSeconds, Defaults defaults, Locators locators) {
    public record Defaults(String mobile, String dependency, String name, String ifsc, String accountNumber,
                           String confirmAccountNumber, String accountType, RunInput.OtpMode otpMode,
                           String fixedOtp, RunInput.BrowserMode browserMode) {}
    public record Locators(String otpSubmitXpath, String micrCss, String bankAddressCss) {}
}
