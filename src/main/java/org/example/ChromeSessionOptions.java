package org.example;

import java.util.Map;
import org.openqa.selenium.chrome.ChromeOptions;

/** Preferences apply only to the fresh automation profile, never the user's normal Chrome profile. */
final class ChromeSessionOptions {
    private ChromeSessionOptions() {}
    static ChromeOptions create(PortalSettings settings,RunInput.BrowserMode mode) {
        ChromeOptions options=new ChromeOptions();options.setBrowserVersion(settings.chromeVersion());
        options.addArguments("--window-size=1440,1000","--disable-notifications","--disable-gpu","--no-sandbox","--remote-debugging-address=127.0.0.1");
        // Chromium: false suppresses the native Save password / No thanks bubble.
        options.setExperimentalOption("prefs",Map.of("credentials_enable_service",false));
        if(mode==RunInput.BrowserMode.HEADLESS)options.addArguments("--headless=new");
        return options;
    }
}
