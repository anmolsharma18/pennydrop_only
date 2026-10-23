package org.example;

import java.nio.file.*;
import java.time.Duration;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

/** Manual, read-only inspection utility. Not a JUnit test; never fills or clicks anything. */
public class ReadOnlyTargetInspector {
    public static void main(String[] args) throws Exception {
        ChromeOptions options=new ChromeOptions();options.setBrowserVersion("151");options.addArguments("--headless=new","--window-size=1440,1000");
        ChromeDriver driver=new ChromeDriver(options);
        try {
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
            driver.get(KycJourney.TARGET);
            StringBuilder evidence=new StringBuilder("# Read-only initial target DOM inspection\n\nNo field was filled and no button was clicked.\n\nChrome: ")
                    .append(driver.getCapabilities().getBrowserVersion()).append("\n\nURL: ").append(driver.getCurrentUrl()).append("\n\n");
            for(String css:new String[]{"#mobile_number","#select_dependancy","#name","#SaveData","#mobile_otp","#micr1","#bank_address","#page-loader","#workflow-otp-loader",".error_msg"}) {
                evidence.append("## ").append(css).append("\n");
                var elements=driver.findElements(By.cssSelector(css));
                if(elements.isEmpty())evidence.append("Not present in initial DOM.\n");
                for(WebElement e:elements)evidence.append("```html\n").append(e.getDomProperty("outerHTML")).append("\n```\nVisible: ").append(e.isDisplayed()).append("\n");
                evidence.append("\n");
            }
            Files.writeString(Path.of("inspection/observed-dom.md"),evidence);
            System.out.println("Read-only DOM evidence saved to inspection/observed-dom.md. No submission performed.");
        } finally { driver.quit(); }
    }
}
