package org.example;

import java.nio.file.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openqa.selenium.chrome.*;
import static org.assertj.core.api.Assertions.*;

/** Checks Chrome's actual temporary profile, including visible mode, without any login or live navigation. */
@EnabledIfSystemProperty(named="localBrowserTest",matches="true")
class LocalChromeProfileTest {
    @ParameterizedTest @EnumSource(RunInput.BrowserMode.class)
    void chromeActuallyDisablesThePasswordSavePrompt(RunInput.BrowserMode mode)throws Exception {
        ChromeOptions options=ChromeSessionOptions.create(new PortalSettings("151",10,3,null,null),mode);
        if(System.getProperty("localChromeBinary")!=null)options.setBinary(System.getProperty("localChromeBinary"));
        ChromeDriver driver=new ChromeDriver(options);
        try {
            var chrome=(java.util.Map<?,?>)driver.getCapabilities().getCapability("chrome");
            Path preferences=Path.of(String.valueOf(chrome.get("userDataDir")),"Default","Preferences");
            var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(preferences));
            assertThat(json.path("credentials_enable_service").isBoolean()).isTrue();
            assertThat(json.path("credentials_enable_service").asBoolean()).isFalse();
        }finally{driver.quit();}
    }
}
