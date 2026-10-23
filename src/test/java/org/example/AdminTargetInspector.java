package org.example;

import java.nio.file.*;
import java.time.Duration;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.*;

/** Manual inspection only. Logs in and reads filter/DOM structure; never clicks a row or Delete. */
public class AdminTargetInspector {
    public static void main(String[] args)throws Exception {
        ChromeOptions options=new ChromeOptions();options.setBrowserVersion("151");options.setBinary(System.getProperty("localChromeBinary"));options.addArguments("--headless=new","--window-size=1440,1000");
        ChromeDriver d=new ChromeDriver(options);
        java.util.Map<String,String> responseIds=new java.util.concurrent.ConcurrentHashMap<>();
        var dev=d.getDevTools();dev.createSession();
        dev.addListener(new org.openqa.selenium.devtools.Event<java.util.Map<String,Object>>("Network.responseReceived",in->(java.util.Map<String,Object>)in.read(java.util.Map.class)),event->{var response=(java.util.Map<String,Object>)event.get("response");if(response!=null&&String.valueOf(response.get("url")).contains("/get_user_data_v2"))responseIds.put(String.valueOf(event.get("requestId")),String.valueOf(response.get("url")));});
        dev.send(new org.openqa.selenium.devtools.Command<>("Network.enable",java.util.Map.of()));
        try {
            d.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));d.get("https://live.meon.co.in/admin/democapital");
            WebDriverWait wait=new WebDriverWait(d,Duration.ofSeconds(40));wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username-input")));
            StringBuilder evidence=new StringBuilder("# Admin DOM inspection (no row action or deletion)\n");
            evidence.append("Login controls:\n").append(d.findElements(By.cssSelector("button,input[type=submit]")).stream().map(e->e.getDomProperty("outerHTML")).toList()).append("\n");
            Files.writeString(Path.of("inspection/admin-dom.md"),evidence);
            if(args.length==0){System.out.println("Initial login controls saved; no login performed");return;}
            d.findElement(By.id("username-input")).sendKeys(System.getenv("KYC_ADMIN_USERNAME"));d.findElement(By.id("password-input")).sendKeys(System.getenv("KYC_ADMIN_PASSWORD"));
            wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button[type=submit],input[type=submit]"))).click();
            wait.until(ExpectedConditions.elementToBeClickable(By.id("makerchecker__li"))).click();
            Thread.sleep(5000);
            var tours=d.findElements(By.cssSelector("[class*='guided-tour']"));
            if(tours.stream().anyMatch(WebElement::isDisplayed)) {
                Files.writeString(Path.of("inspection/admin-tour.md"),tours.stream().filter(e->!e.getTagName().equalsIgnoreCase("body")&&e.isDisplayed()).map(e->e.getDomProperty("outerHTML")).collect(java.util.stream.Collectors.joining("\n")));
                var close=d.findElements(By.cssSelector(".guided-tour-step-button-close[title='End tour']")).stream().filter(WebElement::isDisplayed).toList();
                if(close.size()!=1){System.out.println("Guided-tour controls recorded; no further action taken");return;}
                close.get(0).click();wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".guided-tour-step-highlight")));
                System.out.println("Onboarding dismissed through observed End tour control; continuing read-only inspection");
            }
            wait.until(ExpectedConditions.elementToBeClickable(By.id("select__worflow_btn"))).click();
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//label[normalize-space()='PENNYDROP ONLY']"))).click();
            // Close dropdown if it remains exposed, using its toggle only.
            if(d.findElements(By.xpath("//label[normalize-space()='PENNYDROP ONLY']")).stream().anyMatch(WebElement::isDisplayed))d.findElement(By.id("select__worflow_btn")).click();
            wait.until(ExpectedConditions.elementToBeClickable(By.id("select__Status_btn"))).click();
            Thread.sleep(3000); // Manual read-only settling; not a workflow action retry.
            evidence.append("Status filter DOM:\n");for(WebElement e:d.findElements(By.cssSelector(".filter_sections")))evidence.append(e.getDomProperty("outerHTML")).append("\n");
            evidence.append("Table headers:\n").append(d.findElements(By.cssSelector("thead")).stream().map(e->e.getText()).toList()).append("\n");
            evidence.append("Row structure (values omitted):\n").append(((JavascriptExecutor)d).executeScript("return [...document.querySelectorAll('tbody tr')].slice(0,1).map(tr=>({attributes:[...tr.attributes].map(a=>a.name),cells:[...tr.querySelectorAll('td')].map(td=>({class:td.className,attributes:[...td.attributes].map(a=>a.name),controls:[...td.querySelectorAll('a,button,span[onclick],input')].map(e=>({tag:e.tagName,id:e.id,class:e.className,onclick:e.getAttribute('onclick')}))}))}));")).append("\n");
            evidence.append("Modal structures:\n");for(WebElement e:d.findElements(By.cssSelector(".modal,#deleteReasonSelect")))evidence.append(e.getDomProperty("outerHTML")).append("\n");
            evidence.append("Scripts:\n").append(((JavascriptExecutor)d).executeScript("return [...document.scripts].map(s=>s.src).filter(Boolean);"));
            evidence.append("\nObserved table API schema (values omitted):\n");
            for(String requestId:responseIds.keySet())try {
                var body=dev.send(new org.openqa.selenium.devtools.Command<java.util.Map<String,Object>>("Network.getResponseBody",java.util.Map.of("requestId",requestId),in->(java.util.Map<String,Object>)in.read(java.util.Map.class)));
                var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(String.valueOf(body.get("body")));evidence.append("Top-level fields: ");json.fieldNames().forEachRemaining(k->evidence.append(k).append(","));evidence.append("\n");
                if(json.path("data").isArray()&&json.path("data").size()>0){evidence.append("Row field names/types: ");json.path("data").get(0).fields().forEachRemaining(e->evidence.append(e.getKey()).append(":").append(e.getValue().getNodeType()).append(","));evidence.append("\n");}
            }catch(Exception ignored){evidence.append("Body unavailable\n");}
            Files.writeString(Path.of("inspection/admin-dom.md"),evidence);
            // Public script assets only; no independent API calls and no destructive actions.
            Object scripts=((JavascriptExecutor)d).executeAsyncScript("const done=arguments[arguments.length-1];const urls=[...document.scripts].map(s=>s.src).filter(u=>u.includes(location.host)&&u.endsWith('.js')&&!u.includes('jquery')&&!u.includes('bootstrap'));Promise.all(urls.map(async u=>({url:u,text:await (await fetch(u)).text()}))).then(done).catch(()=>done([]));");
            Files.writeString(Path.of("inspection/admin-scripts.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(scripts));
            System.out.println("Read-only authenticated admin/filter structure saved. No row menu or deletion clicked.");
        }finally{d.quit();}
    }
}
