package org.example;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

final class BankDetailsPage {
    private final WebDriver driver;
    private final WebDriverWait wait;
    private final PortalSettings settings;
    private final RunRecord run;
    BankDetailsPage(WebDriver driver,WebDriverWait wait,PortalSettings settings,RunRecord run) {
        this.driver=driver;this.wait=wait;this.settings=settings;this.run=run;
    }
    void fillAndVerify(RunInput input) {
        if(run.workflowStage!=WorkflowStage.FILLING_BANK || !run.otpEntered || !run.otpSubmitted || !JourneyPage.activeBank(driver))
            throw new IllegalStateException("BankDetailsPage requires an active bank section after this run submitted OTP");
        run.stage("BankDetailsPage: entering portal IFSC and triggering normal blur");
        PageActions.fill(driver,wait,TargetLocators.IFSC,input.ifsc(),run);
        PageActions.field(driver,wait,TargetLocators.IFSC,run).sendKeys(Keys.TAB);
        By micr=By.cssSelector(settings.locators().micrCss()),address=By.cssSelector(settings.locators().bankAddressCss());
        wait.until(d->{run.checkCancelled();String errors=JourneyPage.errors(d);if(!errors.isBlank())throw new IllegalStateException("Target validation: "+errors);
            return !PageActions.populatedValue(d,micr).isBlank()&&!PageActions.populatedValue(d,address).isBlank();});
        run.stage("Automatic MICR and bank address populated; entering portal account values");
        PageActions.fill(driver,wait,TargetLocators.ACCOUNT,input.accountNumber(),run);
        PageActions.fill(driver,wait,TargetLocators.CONFIRM,input.confirmAccountNumber(),run);
        PageActions.select(driver,wait,TargetLocators.ACCOUNT_TYPE,input.accountType(),run);
        PageActions.verify(PageActions.field(driver,wait,TargetLocators.IFSC,run),input.ifsc(),"IFSC");
        PageActions.verify(PageActions.field(driver,wait,TargetLocators.ACCOUNT,run),input.accountNumber(),"Account number");
        PageActions.verify(PageActions.field(driver,wait,TargetLocators.CONFIRM,run),input.confirmAccountNumber(),"Confirmation");
        if(!new Select(PageActions.field(driver,wait,TargetLocators.ACCOUNT_TYPE,run)).getFirstSelectedOption().getText().equals(input.accountType()))
            throw new IllegalStateException("Selected account type did not match portal input");
        if(PageActions.populatedValue(driver,micr).isBlank()||PageActions.populatedValue(driver,address).isBlank())throw new IllegalStateException("Automatic bank fields became empty");
        String errors=JourneyPage.errors(driver);if(!errors.isBlank())throw new IllegalStateException("Target validation: "+errors);
        run.stage("BankDetailsPage verified portal values and automatic fields; ready for requested bank popup flow");
    }
}
