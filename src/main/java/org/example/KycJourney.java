package org.example;

import java.security.SecureRandom;
import java.time.Duration;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.*;
import org.springframework.stereotype.Component;

@Component
public class KycJourney {
    public static final String TARGET="https://live.meon.co.in/democapital/pennydrop_only";
    private final PortalSettings settings;
    private final AdminCleanupProperties admin;
    private final SecureRandom random=new SecureRandom();
    public KycJourney(PortalSettings settings) { this(settings,new AdminCleanupProperties(false,"","")); }
    @org.springframework.beans.factory.annotation.Autowired
    public KycJourney(PortalSettings settings,AdminCleanupProperties admin) { this.settings=settings;this.admin=admin; }
    public String randomOtp() { return String.format(Locale.ROOT,"%04d",random.nextInt(10000)); }
    public void execute(RunInput input,RunRecord run) { executeAt(input,run,TARGET); }
    void executeAt(RunInput input,RunRecord run,String target) {
        ChromeDriver driver=null;BrowserNetwork monitor=null;
        Set<String> inspectedRequests=new HashSet<>();List<Map<String,Object>> outcomes=new ArrayList<>();
        JourneyRecordIdentity identity=new JourneyRecordIdentity();
        try {
            run.status="RUNNING";run.transition(WorkflowStage.APPLICANT_DETAILS,"Starting fresh Chrome session");
            ChromeOptions options=ChromeSessionOptions.create(settings,input.browserMode());
            if(!TARGET.equals(target)&&System.getProperty("localChromeBinary")!=null)options.setBinary(System.getProperty("localChromeBinary"));
            run.diagnostic("passwordSavePrompt","Disabled in this fresh automation Chrome profile");
            driver=new ChromeDriver(options);run.checkCancelled();
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(10));
            if(!driver.getCapabilities().getBrowserVersion().startsWith(settings.chromeVersion()+"."))throw new IllegalStateException("Chrome/CDP major version mismatch");
            monitor=new BrowserNetwork(driver,run.network);
            run.stage("Opening target journey");driver.get(target);run.checkCancelled();
            WebDriverWait wait=new WebDriverWait(driver,Duration.ofSeconds(settings.waitSeconds()));wait.ignoring(StaleElementReferenceException.class);
            wait.until(d->{run.checkCancelled();return JourneyPage.findActive(d,TargetLocators.MOBILE,false);});
            run.stage("Filling portal mobile, dependency and name in active applicant section");
            PageActions.fill(driver,wait,TargetLocators.MOBILE,input.mobile(),run);
            PageActions.select(driver,wait,TargetLocators.DEPENDENCY,input.dependency(),run);
            PageActions.fill(driver,wait,TargetLocators.NAME,input.name(),run);
            run.stage("Sending OTP once");clickOnce(driver,wait,TargetLocators.SEND_OTP,run);
            run.transition(WorkflowStage.WAITING_FOR_OTP,"Waiting for active OTP section and checking Send OTP result");
            BrowserNetwork activeMonitor=monitor;
            WebElement otp=wait.until(d->{
                run.checkCancelled();requireStage(run,WorkflowStage.WAITING_FOR_OTP);
                if(run.otpSubmitted)throw new IllegalStateException("OTP wait cannot run after OTP submission");
                inspectOutcomes(activeMonitor,run,inspectedRequests,outcomes,true);
                d.switchTo().defaultContent();failOnErrors(d);
                if(JourneyPage.visible(d,By.id("redirectModal")))throw new IllegalStateException("Target requires redirect confirmation: "+JourneyPage.safeText(d.findElement(By.id("redirectModal_msg")).getText()));
                // Resolve an active OTP section first, including frames; unrelated IFSC copies are irrelevant.
                WebElement found=JourneyPage.findActive(d,TargetLocators.OTP,false);
                if(found!=null){run.diagnostic("otpPage",JourneyPage.describeCurrent(d,run));return found;}
                d.switchTo().defaultContent();
                if(JourneyPage.visible(d,By.cssSelector(".dynLengthSet input[name='extraNameKey']")))throw new IllegalStateException("Target uses segmented auto-submit OTP fields instead of the supplied mobile_otp input; no OTP was entered");
                if(JourneyPage.findActive(d,TargetLocators.IFSC,true)!=null){
                    run.diagnostic("unexpectedTransition",JourneyPage.describeCurrent(d,run));
                    throw new IllegalStateException("Unexpected direct bank-page transition: active bank container and enabled bank fields observed before this run entered and submitted OTP");
                }
                return null;
            });
            run.transition(WorkflowStage.ENTERING_OTP,"Entering one test OTP in active mobile_otp field");
            String otpValue=input.otpMode()==RunInput.OtpMode.FIXED?input.fixedOtp():randomOtp();
            run.checkCancelled();otp.clear();otp.sendKeys(otpValue);PageActions.verify(otp,otpValue,"OTP");run.markOtpEntered();
            WebElement submit=wait.until(d->{
                run.checkCancelled();requireStage(run,WorkflowStage.ENTERING_OTP);
                WebElement activeOtp=JourneyPage.activeField(d,TargetLocators.OTP);
                if(activeOtp==null)throw new IllegalStateException("OTP section changed before this run clicked Submit; stopping without another click");
                if(d.findElements(TargetLocators.LOADERS).stream().anyMatch(WebElement::isDisplayed))return null;
                for(WebElement button:d.findElements(By.xpath(settings.locators().otpSubmitXpath())))
                    if(Boolean.TRUE.equals(JourneyPage.fieldState(d,button).get("active"))&&JourneyPage.sameSection(d,activeOtp,button))return button;
                return null;
            });
            run.checkCancelled();run.stage("OTP Submit click (once)");
            submit.click();
            run.markOtpSubmitted(); // Immediately after the actual click; before any further browser call/wait.
            run.transition(WorkflowStage.WAITING_FOR_BANK,"Waiting for active bank section after OTP Submit");
            wait.until(d->{
                run.checkCancelled();requireStage(run,WorkflowStage.WAITING_FOR_BANK);
                if(!run.otpEntered||!run.otpSubmitted)throw new IllegalStateException("Bank wait requires OTP entry and Submit by this run");
                inspectOutcomes(activeMonitor,run,inspectedRequests,outcomes,true);
                d.switchTo().defaultContent();failOnErrors(d);
                if(JourneyPage.findActive(d,TargetLocators.OTP,false)!=null)return null;
                WebElement bank=JourneyPage.findActive(d,TargetLocators.IFSC,true);
                if(bank!=null)run.diagnostic("bankPage",JourneyPage.describeCurrent(d,run));
                return bank;
            });
            run.transition(WorkflowStage.FILLING_BANK,"Active bank section confirmed; calling BankDetailsPage");
            new BankDetailsPage(driver,wait,settings,run).fillAndVerify(input);
            if(admin.enabled())identity.capture(driver,monitor,run);
            BankPopupFlow.complete(driver,wait,run);
            run.diagnostic("pennyDropCaptured",true);
            if(admin.enabled()) {
                identity.capture(driver,monitor,run);
                AdminCleanupFlow.complete(driver,wait,run,input,identity.unique(),monitor,AdminCredentials.load(Path.of(admin.credentialsDirectory())),admin.url());
            }
            run.transition(WorkflowStage.COMPLETED,admin.enabled()?"Penny-drop result and admin deletion success popup captured":"Bank popup captured, Proceed clicked once and resulting page captured");
            run.diagnostic("completedPage",JourneyPage.describeCurrent(driver,run));
            run.status="SETTLING";
        } catch(Exception e) {
            if(driver!=null){run.diagnostic("failurePage",JourneyPage.describe(driver,run));screenshot(driver,run,"failure");}
            run.error=e instanceof TimeoutException?"Timed out in "+run.workflowStage+": "+run.step+". See OTP/IFSC container visibility, enabled state and API outcomes in diagnostics. No action was retried.":e.getClass().getSimpleName()+": "+safeMessage(e);
            if(e instanceof TimeoutException&&run.workflowStage==WorkflowStage.WAITING_FOR_OTP)run.error="Send OTP did not produce a visible, enabled //input[@id='mobile_otp'] in an active container within "+settings.waitSeconds()+" seconds. See diagnostics; HTTP 200 alone does not confirm OTP acceptance. No action was retried.";
            if(e instanceof TimeoutException&&run.workflowStage.ordinal()>=WorkflowStage.ADMIN_LOGIN.ordinal())run.error="Admin cleanup timed out in "+run.workflowStage+": "+run.step+". No matched entry, unavailable controls, or unconfirmed success; review admin search diagnostics and actual API outcomes. No delete action was retried.";
            run.status="SETTLING";run.stage(e instanceof CancellationException?"Run cancelled; collecting remaining events before browser cleanup":"Workflow failed; collecting remaining network events");
        } finally {
            if(monitor!=null){
                long deadline=System.nanoTime()+Duration.ofSeconds(settings.settleSeconds()).toNanos();
                try {while(System.nanoTime()<deadline&&!run.network.settled(1500))Thread.sleep(100);}
                catch(InterruptedException e){Thread.currentThread().interrupt();if(run.error==null)run.error="Run interrupted during settling";}
                try {inspectOutcomes(monitor,run,inspectedRequests,outcomes,false);}catch(Exception ignored){}
            }
            run.network.freeze();
            try {if(monitor!=null)monitor.close();}catch(Exception e){run.stage("Network cleanup warning: "+e.getClass().getSimpleName());}
            try {if(driver!=null)driver.quit();}catch(Exception e){run.stage("Browser cleanup warning: "+e.getClass().getSimpleName());}
            run.finishedAt=java.time.Instant.now().toString();run.status=run.isCancellationRequested()?"CANCELLED":run.error==null?"SUCCEEDED":"FAILED";
        }
    }
    private static void requireStage(RunRecord run,WorkflowStage expected){if(run.workflowStage!=expected)throw new IllegalStateException("Expected "+expected+", observed "+run.workflowStage);}
    private static void failOnErrors(WebDriver d){String errors=JourneyPage.errors(d);if(!errors.isBlank())throw new IllegalStateException("Target validation: "+errors);}
    private static void clickOnce(WebDriver d,WebDriverWait wait,By locator,RunRecord run){
        WebElement button=wait.until(driver->{run.checkCancelled();if(driver.findElements(TargetLocators.LOADERS).stream().anyMatch(WebElement::isDisplayed))return null;return JourneyPage.activeField(driver,locator);});
        run.checkCancelled();button.click();
    }
    private static void inspectOutcomes(BrowserNetwork monitor,RunRecord run,Set<String> seen,List<Map<String,Object>> outcomes,boolean enforce){
        for(var response:run.network.snapshot()){
            boolean send=response.step().equals("Sending OTP once"),submit=response.step().equals("OTP Submit click (once)");
            boolean bank=response.step().equals("Bank SaveData click (once)"),proceed=response.step().equals("Bank popup Proceed click (once)");
            if((!send&&!submit&&!bank&&!proceed)||!response.state().equals("COMPLETED")||!seen.add(response.requestId()+":"+response.hop()))continue;
            Map<String,Object> feedback=monitor.applicationFeedback(response.requestId());
            Map<String,Object> summary=new LinkedHashMap<>(feedback);summary.put("action",send?"SEND_OTP":submit?"OTP_SUBMIT":bank?"BANK_SUBMIT":"BANK_POPUP_PROCEED");summary.put("requestId",response.requestId());if(response.status()!=null)summary.put("httpStatus",response.status());
            outcomes.add(summary);run.diagnostic("apiOutcomes",List.copyOf(outcomes));run.diagnostic(send?"sendOtpResponse":submit?"otpSubmitResponse":bank?"bankSubmitResponse":"bankPopupProceedResponse",summary);
            if(enforce&&Boolean.FALSE.equals(feedback.get("success"))&&(submit||!Boolean.TRUE.equals(feedback.get("redirect"))))throw new IllegalStateException((send?"Send OTP":"OTP Submit")+" was rejected by the application (HTTP "+response.status()+"): "+feedback.getOrDefault("msg",feedback.getOrDefault("message","success=false"))+". No request was retried.");
        }
    }
    static Path screenshotPath(String id){return Path.of("target","run-artifacts",UUID.fromString(id).toString(),"screenshot.png").toAbsolutePath();}
    private static void screenshot(ChromeDriver driver,RunRecord run,String label){
        try {RunScreenshots.capture(driver,run,label);}
        catch(Exception e){run.diagnostic("screenshotNote","Screenshot unavailable: "+e.getClass().getSimpleName());}
    }
    private static String safeMessage(Exception e){String m=e.getMessage();return m==null?"See current stage":m.split("\\R",2)[0];}
}
