package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.WebDriverWait;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named="localBrowserTest", matches="true")
class LocalTourDismissalTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings={"normal", "intercepted", "hidden-first", "library-stop", "broken-api", "force", "delayed"})
    void closesTourWithoutRemovingBusinessDialogs(String mode) throws Exception {
        String handler = mode.equals("library-stop") || mode.equals("broken-api") || mode.equals("force") ? "" :
                "document.querySelector('.guided-tour-step').remove()";
        String first = mode.equals("hidden-first") ? "<span title='End tour' hidden onclick=\""+handler+"\"></span>" : "";
        String blocker = mode.equals("intercepted") ? "<div style='position:fixed;inset:0;z-index:100'></div>" : "";
        String api = mode.equals("library-stop") ? "var tourguide={stop(){document.querySelector('.guided-tour-step').remove()}};" :
                mode.equals("broken-api") ? "var tourguide={stop(){throw new Error('Fixture broken stop')}};" : "";
        Path page=directory.resolve("tour.html");
        String tour = mode.equals("delayed") ? "" :
            "<div class='guided-tour-step'><div class='guided-tour-step-tooltip'><span title='End tour' onclick=\""+handler+
            "\">End tour</span></div></div>";
        Files.writeString(page,"<html><body class='"+(mode.equals("delayed")?"":"guided-tour no-scroll")+"'>"+first+
            "<div id='deleteModal'>Business confirmation</div><div class='overlay-bg'>Loading indicator</div>"+
            tour+blocker+"<script>"+api+"</script></body></html>");
        ChromeOptions options=new ChromeOptions();options.setBrowserVersion("151");options.addArguments("--headless=new","--disable-gpu","--no-sandbox","--window-size=1440,1000");
        if(System.getProperty("localChromeBinary")!=null)options.setBinary(System.getProperty("localChromeBinary"));
        ChromeDriver driver=new ChromeDriver(options);
        try {
            driver.get(page.toUri().toString());
            RunRecord run=new RunRecord(RunRecord.Kind.DELETE_ONLY);
            AdminTourDismissal guard=new AdminTourDismissal();
            guard.dismissIfVisible(driver,run);
            if(mode.equals("delayed"))((JavascriptExecutor)driver).executeScript("""
                document.body.classList.add('guided-tour','no-scroll');
                document.body.insertAdjacentHTML('beforeend',
                  `<div class="guided-tour-step"><div class="guided-tour-step-highlight"></div><div class="guided-tour-step-tooltip"><span title="End tour">End tour</span></div></div>`);
                """);
            new WebDriverWait(driver,Duration.ofSeconds(10)).until(d->{guard.dismissIfVisible(d,run);return !JourneyPage.visible(d,AdminTourDismissal.VISIBLE);});
            guard.dismissIfVisible(driver,run);
            var diagnostics=(java.util.Map<?,?>)run.view().get("diagnostics");
            assertThat(diagnostics.get("adminTourDismissed")).isEqualTo(true);
            assertThat(diagnostics.get("adminTourGuardArmed")).isEqualTo(true);
            assertThat(diagnostics.get("adminTourLocator")).isEqualTo("(//span[@title='End tour'])[1]");
            assertThat(diagnostics.get("adminTourMethod")).isEqualTo("javascript-force-close-with-observer");
            assertThat(driver.findElement(By.id("deleteModal")).isDisplayed()).isTrue();
            assertThat(driver.findElement(By.cssSelector(".overlay-bg")).isDisplayed()).isTrue();
            run.requestCancel();assertThatThrownBy(()->guard.dismissIfVisible(driver,run)).isInstanceOf(java.util.concurrent.CancellationException.class);
        } finally {driver.quit();}
    }
}

