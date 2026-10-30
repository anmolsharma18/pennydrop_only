package org.example;

import java.net.URI;
import java.util.*;
import org.openqa.selenium.*;

/** Active sections require an exposed container and visible, enabled fields; DOM presence is insufficient. */
final class JourneyPage {
    private JourneyPage() {}
    static String safeText(String text) {
        if(text==null) return "";
        String safe=text.replaceAll("(?i)<[^>]*>"," ").replaceAll("[0-9]{4,}","[redacted]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}","[redacted email]").strip();
        return safe.substring(0,Math.min(safe.length(),300));
    }
    static String endpoint(String url) {
        try {URI u=URI.create(url);return new URI(u.getScheme(),null,u.getHost(),u.getPort(),u.getPath(),null,null).toString();}
        catch(Exception e) {return "[unavailable URL]";}
    }
    @SuppressWarnings("unchecked")
    static Map<String,Object> fieldState(WebDriver d,WebElement e) {
        // Read-only: no input values, event handlers, styles or validation are modified.
        Map<String,Object> container=(Map<String,Object>)((JavascriptExecutor)d).executeScript("""
            const e=arguments[0];
            const c=e.closest('[data-kyc-page],[data-page],[data-stage],section,form,[role="tabpanel"],[role="dialog"],.tab-pane,.page-section,.step-content,.data-container-child') || e.closest('.form-item') || e.parentElement;
            let exposed=true;
            for(let n=e;n;n=n.parentElement){const s=getComputedStyle(n);if(n.hidden||n.hasAttribute('inert')||n.getAttribute('aria-hidden')==='true'||s.display==='none'||s.visibility==='hidden'||s.visibility==='collapse'){exposed=false;break;}}
            const s=c?getComputedStyle(c):null;
            return {tag:c?c.tagName.toLowerCase():'',id:c?c.id:'',display:s?s.display:'',visibility:s?s.visibility:'',hidden:c?c.hidden:false,ariaHidden:c?c.getAttribute('aria-hidden'):null,visible:!!c&&c.getClientRects().length>0&&exposed};
            """,e);
        boolean displayed=e.isDisplayed(),enabled=e.isEnabled();
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",Objects.toString(e.getDomAttribute("id"),"")); result.put("name",Objects.toString(e.getDomAttribute("name"),""));
        result.put("type",Objects.toString(e.getDomAttribute("type"),""));result.put("visible",displayed);result.put("enabled",enabled);
        result.put("container",container);result.put("active",displayed && enabled && Boolean.TRUE.equals(container.get("visible")));
        return result;
    }
    static WebElement activeField(WebDriver d,By locator) {
        for(WebElement e:d.findElements(locator)) if(Boolean.TRUE.equals(fieldState(d,e).get("active"))) return e;
        return null;
    }
    static boolean activeBank(WebDriver d) {
        return activeField(d,TargetLocators.IFSC)!=null && activeField(d,TargetLocators.ACCOUNT)!=null
                && activeField(d,TargetLocators.CONFIRM)!=null && activeField(d,TargetLocators.ACCOUNT_TYPE)!=null;
    }
    static boolean sameSection(WebDriver d,WebElement field,WebElement button) {
        return Boolean.TRUE.equals(((JavascriptExecutor)d).executeScript("""
            const sel='[data-kyc-page],[data-page],[data-stage],section,form,[role="tabpanel"],[role="dialog"],.tab-pane,.page-section,.step-content,.data-container-child';
            const parent=e=>e.closest(sel)||e.closest('.form-item')||e.parentElement;
            return parent(arguments[0])===parent(arguments[1]);
            """,field,button));
    }
    @SuppressWarnings("unchecked")
    static Map<String,Object> clickReadiness(WebDriver d,WebElement element) {
        // Scroll only; the hit test is read-only and never invokes the element's click handler.
        return (Map<String,Object>)((JavascriptExecutor)d).executeScript("""
            const e=arguments[0];
            e.scrollIntoView({block:'center',inline:'center',behavior:'auto'});
            const r=e.getBoundingClientRect();
            const x=Math.max(0,Math.min(window.innerWidth-1,r.left+r.width/2));
            const y=Math.max(0,Math.min(window.innerHeight-1,r.top+r.height/2));
            const top=document.elementFromPoint(x,y);
            const inViewport=r.width>0&&r.height>0&&r.top>=0&&r.left>=0&&r.bottom<=window.innerHeight&&r.right<=window.innerWidth;
            const unobstructed=!!top&&(top===e||e.contains(top));
            const safe=v=>(v||'').toString().slice(0,120);
            return {inViewport,unobstructed,x:Math.round(x),y:Math.round(y),
                blockerTag:top?safe(top.tagName).toLowerCase():'',blockerId:top?safe(top.id):'',blockerClass:top?safe(top.className):''};
            """,element);
    }
    static boolean visible(WebDriver d,By locator) {return d.findElements(locator).stream().anyMatch(WebElement::isDisplayed);}
    static String errors(WebDriver d) {
        return d.findElements(TargetLocators.ERRORS).stream().filter(WebElement::isDisplayed).map(WebElement::getText)
                .map(JourneyPage::safeText).filter(s->!s.isBlank()).distinct().limit(5).reduce((a,b)->a+"; "+b).orElse("");
    }
    static WebElement findActive(WebDriver driver,By locator,boolean bank) {
        driver.switchTo().defaultContent();return search(driver,locator,bank,0);
    }
    private static WebElement search(WebDriver d,By locator,boolean bank,int depth) {
        WebElement field=activeField(d,locator);
        if(field!=null && (!bank || activeBank(d))) return field;
        if(depth>=4) return null;
        for(WebElement frame:d.findElements(By.cssSelector("iframe,frame"))) {
            if(!frame.isDisplayed()) continue;
            d.switchTo().frame(frame);WebElement found=search(d,locator,bank,depth+1);
            if(found!=null) return found;
            d.switchTo().parentFrame();
        }
        return null;
    }
    static Map<String,Object> describeCurrent(WebDriver d,RunRecord run) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("workflowStage",run.workflowStage.name());result.put("otpEntered",run.otpEntered);result.put("otpSubmitted",run.otpSubmitted);
        result.put("currentUrl",endpoint(d.getCurrentUrl()));
        List<Map<String,Object>> otp=states(d,TargetLocators.OTP),ifsc=states(d,TargetLocators.IFSC);
        result.put("otpInputCount",otp.size());result.put("ifscInputCount",ifsc.size());result.put("otpElements",otp);result.put("ifscElements",ifsc);
        result.put("otpActive",activeField(d,TargetLocators.OTP)!=null);result.put("bankActive",activeBank(d));
        result.put("visibleFields",d.findElements(By.cssSelector("input,select,textarea")).stream().filter(WebElement::isDisplayed).limit(30)
                .map(e->Map.of("tag",e.getTagName(),"id",Objects.toString(e.getDomAttribute("id"),""),"name",Objects.toString(e.getDomAttribute("name"),""))).toList());
        result.put("iframeCount",d.findElements(By.cssSelector("iframe,frame")).size());result.put("validation",errors(d));return result;
    }
    private static List<Map<String,Object>> states(WebDriver d,By locator) {return d.findElements(locator).stream().map(e->fieldState(d,e)).toList();}
    static Map<String,Object> describe(WebDriver d,RunRecord run) {
        try {d.switchTo().defaultContent();return describeCurrent(d,run);}
        catch(Exception e) {return Map.of("workflowStage",run.workflowStage.name(),"otpEntered",run.otpEntered,"otpSubmitted",run.otpSubmitted,"inspectionNote","Page changed during inspection");}
    }
}
