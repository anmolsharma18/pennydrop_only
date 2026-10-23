package org.example;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.*;
import java.time.Duration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"portal.mail.enabled=false","portal.mail.host=","portal.profile-directory=${java.io.tmpdir}/penny-profile-${random.uuid}","portal.scheduler-enabled=false","portal.scheduler-directory=${java.io.tmpdir}/penny-ui-tests-${random.uuid}"})
@EnabledIfSystemProperty(named="localBrowserTest",matches="true")
class LocalPortalBrowserTest {
    @LocalServerPort int port;
    @MockitoBean KycJourney journey; // Real portal, mocked workflow: even Run cannot reach live site.
    @MockitoBean AdminDeleteJourney deletion;
    @Test void portalRendersAndSubmitsStringValuesOnlyOnClick() throws Exception {
        doAnswer(call->{RunRecord run=call.getArgument(1);run.workflowStage=WorkflowStage.COMPLETED;run.status="SUCCEEDED";return null;}).when(deletion).execute(any(),any());
        doAnswer(call->{RunRecord run=call.getArgument(1);
            byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lN8AAAAASUVORK5CYII=");
            for(String label:new String[]{"bank-popup","result-page","admin-delete-confirmation","admin-delete-success"}){Path file=RunScreenshots.path(run.id,label);Files.createDirectories(file.getParent());Files.write(file,png);run.screenshot(label,"/api/runs/"+run.id+"/screenshots/"+label);}
            run.diagnostic("resultPage",java.util.Map.of("url","http://127.0.0.1:"+port+"/fixture-result"));
            run.workflowStage=WorkflowStage.COMPLETED;run.stage("Local UI test completed");run.status="SUCCEEDED";return null;}).when(journey).execute(any(),any());
        ChromeOptions options=new ChromeOptions();options.setBrowserVersion("151");options.addArguments("--headless=new","--window-size=1440,1100");
        if(System.getProperty("localChromeBinary")!=null)options.setBinary(System.getProperty("localChromeBinary"));
        ChromeDriver driver=new ChromeDriver(options);
        try {
            driver.get("http://127.0.0.1:"+port+"/");
            WebDriverWait wait=new WebDriverWait(driver,Duration.ofSeconds(15));
            wait.until(ExpectedConditions.elementToBeClickable(By.id("runButton")));
            verifyNoInteractions(journey);
            assertThat(driver.findElement(By.id("fixedOtpLabel")).isDisplayed()).isFalse();
            new Select(driver.findElement(By.name("otpMode"))).selectByValue("FIXED");
            assertThat(driver.findElement(By.id("fixedOtpLabel")).isDisplayed()).isTrue();
            for(var entry:java.util.Map.of("mobile","6999999999","dependency","Fixture dependency","name","QA Fixture","ifsc","TEST0000000","accountNumber","0000123","confirmAccountNumber","0000123","accountType","Fixture account","fixedOtp","0007").entrySet()) {
                WebElement field=driver.findElement(By.name(entry.getKey()));field.clear();field.sendKeys(entry.getValue());
            }
            ((JavascriptExecutor)driver).executeScript("document.querySelector('[name=scheduledLocalTime]').value='2099-10-15T10:30';");
            driver.findElement(By.name("notificationEmails")).sendKeys("one@example.test; two@example.test");
            driver.findElement(By.id("saveDetails")).click();
            wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("savedDetailsMessage"),"saved on this computer"));
            driver.navigate().refresh();wait.until(ExpectedConditions.elementToBeClickable(By.id("runButton")));
            assertThat(driver.findElement(By.name("accountNumber")).getDomProperty("value")).isEqualTo("0000123");
            assertThat(driver.findElement(By.name("fixedOtp")).getDomProperty("value")).isEqualTo("0007");
            assertThat(driver.findElement(By.name("notificationEmails")).getDomProperty("value")).isEqualTo("one@example.test; two@example.test");
            driver.findElement(By.id("scheduleButton")).click();
            wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("#scheduleBody tr"),1));
            assertThat(driver.findElement(By.id("scheduleBody")).getText()).contains("2099-10-15 10:30 IST","PENDING","QA Fixture");verifyNoInteractions(journey);
            driver.findElement(By.name("accountNumber")).clear();driver.findElement(By.name("accountNumber")).sendKeys("1234");
            driver.findElement(By.xpath("//tbody[@id='scheduleBody']//button[text()='Load inputs']")).click();
            wait.until(d->d.findElement(By.name("accountNumber")).getAttribute("value").equals("0000123"));
            driver.findElement(By.xpath("//tbody[@id='scheduleBody']//button[text()='Cancel schedule']")).click();
            wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("scheduleBody"),"CANCELLED"));
            driver.findElement(By.xpath("//tbody[@id='scheduleBody']//button[text()='Delete']")).click();
            wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("#scheduleBody tr"),0));
            Files.copy(driver.getScreenshotAs(OutputType.FILE).toPath(),Path.of("target/portal-preview.png"),StandardCopyOption.REPLACE_EXISTING);
            driver.findElement(By.id("runButton")).click();
            wait.until(ExpectedConditions.textToBe(By.id("status"),"SUCCEEDED"));
            verify(journey,times(1)).execute(argThat(i->i.accountNumber().equals("0000123") && i.fixedOtp().equals("0007")),any());
            assertThat(driver.findElement(By.id("formError")).getText()).isEmpty();
            wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("#screenshotGallery img"),4));
            wait.until(d->Boolean.TRUE.equals(((JavascriptExecutor)d).executeScript("return [...document.querySelectorAll('#screenshotGallery img')].every(img=>img.complete&&img.naturalWidth>0);")));
            assertThat(driver.findElement(By.id("screenshotsPanel")).isDisplayed()).isTrue();
            assertThat(driver.findElement(By.id("screenshotGallery")).getText()).contains("Admin deletion success popup");
            assertThat(driver.findElement(By.id("resultPageUrl")).getText()).contains("/fixture-result");
            wait.until(ExpectedConditions.elementToBeClickable(By.id("deleteOnlyButton")));
            for(String key:new String[]{"dependency","accountType","confirmAccountNumber","fixedOtp"})driver.findElement(By.name(key)).clear();
            driver.findElement(By.id("deleteOnlyButton")).click();
            wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("workflowStage"),"Delete only"));
            wait.until(ExpectedConditions.textToBe(By.id("status"),"SUCCEEDED"));
            verify(deletion,timeout(1000)).execute(argThat(i->i.mobile().equals("6999999999")&&i.accountNumber().equals("0000123")),argThat(r->r.kind==RunRecord.Kind.DELETE_ONLY&&!r.otpSubmitted));
            verify(journey,times(1)).execute(any(),any());assertThat(driver.findElement(By.id("formError")).getText()).isEmpty();
            driver.findElement(By.id("clearDetails")).click();
            wait.until(d->d.findElement(By.name("notificationEmails")).getDomProperty("value").isEmpty());
            driver.navigate().refresh();wait.until(ExpectedConditions.elementToBeClickable(By.id("runButton")));
            assertThat(driver.findElement(By.name("notificationEmails")).getDomProperty("value")).isEmpty();
            assertThat(driver.findElement(By.name("mobile")).getDomProperty("value")).isEmpty();
        } finally { driver.quit(); }
    }
}


