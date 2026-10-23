package org.example;

import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

/** The explicitly requested third-page submission and popup continuation; consequential clicks never retry. */
final class BankPopupFlow {
    static void complete(WebDriver driver,WebDriverWait wait,RunRecord run) throws java.io.IOException {
        if(run.workflowStage!=WorkflowStage.FILLING_BANK||!run.otpSubmitted||!JourneyPage.activeBank(driver))
            throw new IllegalStateException("Bank popup flow requires verified active bank details after OTP Submit");
        run.transition(WorkflowStage.SUBMITTING_BANK,"Clicking third-page SaveData once");
        WebElement bankButton=wait.until(d->{
            run.checkCancelled();
            if(!JourneyPage.activeBank(d))throw new IllegalStateException("Bank section changed before SaveData click");
            if(d.findElements(TargetLocators.LOADERS).stream().anyMatch(WebElement::isDisplayed))return null;
            return JourneyPage.activeField(d,TargetLocators.BANK_SUBMIT);
        });
        run.checkCancelled();run.stage("Bank SaveData click (once)");bankButton.click();
        run.transition(WorkflowStage.WAITING_FOR_BANK_POPUP,"Waiting for visible bankPopUp after third-page submission");
        wait.until(d->{run.checkCancelled();String errors=JourneyPage.errors(d);if(!errors.isBlank())throw new IllegalStateException("Bank submission: "+errors);return JourneyPage.activeField(d,TargetLocators.BANK_POPUP);});
        // Required screenshot must be persisted/published before proceeding. Later images never overwrite it.
        RunScreenshots.capture(driver,run,"bank-popup");
        run.stage("Bank popup screenshot saved and available in portal");
        run.diagnostic("bankPopup",Map.of("currentUrl",JourneyPage.endpoint(driver.getCurrentUrl()),"screenshotUrl","/api/runs/"+run.id+"/screenshots/bank-popup"));
        run.transition(WorkflowStage.CONFIRMING_BANK_POPUP,"Waiting for the specified bank popup Proceed button");
        WebElement confirm=wait.until(d->{
            run.checkCancelled();WebElement popup=JourneyPage.activeField(d,TargetLocators.BANK_POPUP);if(popup==null)throw new IllegalStateException("Bank popup disappeared before confirmation");
            for(WebElement button:d.findElements(TargetLocators.BANK_POPUP_CONFIRM))
                if(Boolean.TRUE.equals(JourneyPage.fieldState(d,button).get("active"))&&Boolean.TRUE.equals(((JavascriptExecutor)d).executeScript("return arguments[0].contains(arguments[1]);",popup,button)))return button;
            return null;
        });
        String beforeUrl=driver.getCurrentUrl();WebElement oldDocument=driver.findElement(By.tagName("html"));
        run.checkCancelled();RunScreenshots.capture(driver,run,"bank-popup-before-proceed");
        run.stage("Popup screenshot saved immediately before the specified Proceed click");
        run.checkCancelled();run.stage("Bank popup Proceed click (once)");confirm.click();
        run.transition(WorkflowStage.WAITING_FOR_RESULT,"Waiting for the page reached after popup Proceed");
        wait.until(d->{
            run.checkCancelled();
            boolean navigation=!beforeUrl.equals(d.getCurrentUrl())||Boolean.TRUE.equals(ExpectedConditions.stalenessOf(oldDocument).apply(d));
            // SPA transitions also qualify when the popup and editable bank screen have both departed.
            boolean spa=!JourneyPage.visible(d,TargetLocators.BANK_POPUP)&&!JourneyPage.activeBank(d);
            if(!navigation&&!spa)return false;
            return "complete".equals(((JavascriptExecutor)d).executeScript("return document.readyState;"))
                    && d.findElements(TargetLocators.LOADERS).stream().noneMatch(WebElement::isDisplayed)
                    && !JourneyPage.visible(d,TargetLocators.BANK_POPUP)&&d.findElement(By.tagName("body")).isDisplayed();
        });
        run.diagnostic("resultPage",Map.of("url",JourneyPage.endpoint(driver.getCurrentUrl()),"screenshotUrl","/api/runs/"+run.id+"/screenshots/result-page"));
        RunScreenshots.capture(driver,run,"result-page");
        run.stage("Resulting page captured and available in portal");
    }
}
