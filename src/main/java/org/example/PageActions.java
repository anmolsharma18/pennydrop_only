package org.example;

import java.util.Objects;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

final class PageActions {
    private PageActions() {}
    static WebElement field(WebDriver d,WebDriverWait wait,By by,RunRecord run) {
        return wait.until(driver->{run.checkCancelled();return JourneyPage.activeField(driver,by);});
    }
    static void fill(WebDriver d,WebDriverWait wait,By by,String text,RunRecord run) {
        WebElement e=field(d,wait,by,run);run.checkCancelled();e.clear();e.sendKeys(text);verify(e,text,by.toString());
    }
    static void verify(WebElement e,String expected,String label) {
        if(!expected.equals(e.getDomProperty("value")))throw new IllegalStateException(label+" value did not match submitted input");
    }
    static void select(WebDriver d,WebDriverWait wait,By by,String label,RunRecord run) {
        Select select=new Select(field(d,wait,by,run));
        if(select.getOptions().stream().noneMatch(o->o.getText().equals(label)))throw new IllegalArgumentException("Exact visible option label '"+label+"' does not exist for "+by);
        run.checkCancelled();select.selectByVisibleText(label);
        if(!select.getFirstSelectedOption().getText().equals(label))throw new IllegalStateException("Selection did not match for "+by);
    }
    static String populatedValue(WebDriver d,By by) {
        for(WebElement e:d.findElements(by)) if(e.isDisplayed())return Objects.toString(e.getDomProperty("value"),"");
        return "";
    }
}
