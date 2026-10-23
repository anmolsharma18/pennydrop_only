package org.example;

import org.openqa.selenium.By;

/** Supplied XPaths are kept literally. Additional source-inspected locators are documented in README. */
public final class TargetLocators {
    private TargetLocators() {}
    public static final By MOBILE = By.xpath("//input[@id='mobile_number']");
    public static final By DEPENDENCY = By.xpath("//select[@id='select_dependancy']");
    public static final By NAME = By.xpath("//input[@id='name']");
    public static final By OTP = By.xpath("//input[@id='mobile_otp']");
    public static final By IFSC = By.xpath("//input[@id='ifsc']");
    public static final By ACCOUNT = By.xpath("//input[@id='account_number']");
    public static final By CONFIRM = By.xpath("//input[@id='reenter_account_number']");
    public static final By ACCOUNT_TYPE = By.xpath("//select[@id='account_type']");
    public static final By SEND_OTP = By.xpath("//button[@id='SaveData' and normalize-space(.)='SEND OTP']");
    public static final By BANK_SUBMIT = By.xpath("//button[@id='SaveData']");
    public static final By BANK_POPUP = By.id("bankPopUp");
    public static final By BANK_POPUP_CONFIRM = By.xpath("//button[@onclick=\"popUp_save('true','bankPopUp');this.disabled = true;\"]");
    public static final By LOADERS = By.cssSelector("#page-loader, #workflow-otp-loader, .overlay-bg, .overlay-bg-meon");
    public static final By ERRORS = By.cssSelector(".error_msg, #successFalse_msg, #errorIfscifsc, #notification, #APIError, #networkErrorPopupMessage, .alert-danger");
}
