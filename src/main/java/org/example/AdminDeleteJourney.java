package org.example;

import java.time.*;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

/** Independent admin run: no applicant/OTP/bank journey navigation and no post-journey gap. */
@Component
public class AdminDeleteJourney {
    private final PortalSettings settings;
    private final AdminCleanupProperties admin;
    public AdminDeleteJourney(PortalSettings settings,AdminCleanupProperties admin){this.settings=settings;this.admin=admin;}
    public void execute(DeleteInput input,RunRecord run) {
        ChromeDriver driver=null;BrowserNetwork monitor=null;
        try {
            if(run.kind!=RunRecord.Kind.DELETE_ONLY)throw new IllegalStateException("Deletion runner requires DELETE_ONLY run");
            run.status="RUNNING";run.stage("Starting fresh Chrome for standalone admin deletion");run.checkCancelled();
            AdminCredentials credentials=AdminCredentials.load(Path.of(admin.credentialsDirectory()));
            ChromeOptions options=ChromeSessionOptions.create(settings,input.browserMode());
            run.diagnostic("passwordSavePrompt","Disabled in this fresh automation Chrome profile");
            if(admin.url().startsWith("http://127.0.0.1:")&&System.getProperty("localChromeBinary")!=null)options.setBinary(System.getProperty("localChromeBinary"));
            driver=new ChromeDriver(options);run.checkCancelled();
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(10));
            if(!driver.getCapabilities().getBrowserVersion().startsWith(settings.chromeVersion()+"."))throw new IllegalStateException("Chrome/CDP major version mismatch");
            monitor=new BrowserNetwork(driver,run.network);
            WebDriverWait wait=new WebDriverWait(driver,Duration.ofSeconds(settings.waitSeconds()));wait.ignoring(StaleElementReferenceException.class);
            AdminCleanupFlow.deleteOnly(driver,wait,run,input,monitor,credentials,admin.url());
            run.transition(WorkflowStage.COMPLETED,"Standalone deletion confirmed; success popup captured");run.status="SETTLING";
        }catch(Exception e){
            if(driver!=null){run.diagnostic("failurePage",JourneyPage.describe(driver,run));try{RunScreenshots.capture(driver,run,"failure");}catch(Exception ignored){run.diagnostic("screenshotNote","Failure screenshot unavailable");}}
            run.error=e instanceof TimeoutException?"Standalone deletion timed out in "+run.workflowStage+". Review matching diagnostics and API outcomes. No delete was retried.":e.getClass().getSimpleName()+": "+JourneyPage.safeText(e.getMessage()==null?"See current stage":e.getMessage().split("\\R",2)[0]);
            run.status="SETTLING";run.stage(e instanceof CancellationException?"Deletion cancelled; collecting network events":"Deletion failed; collecting network events");
        }finally{
            if(monitor!=null){long deadline=System.nanoTime()+Duration.ofSeconds(settings.settleSeconds()).toNanos();try{while(System.nanoTime()<deadline&&!run.network.settled(1500))Thread.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();if(run.error==null)run.error="Deletion interrupted during network settling";}}
            run.network.freeze();try{if(monitor!=null)monitor.close();}catch(Exception e){run.stage("Network cleanup warning: "+e.getClass().getSimpleName());}
            try{if(driver!=null)driver.quit();}catch(Exception e){run.stage("Browser cleanup warning: "+e.getClass().getSimpleName());}
            run.finishedAt=Instant.now().toString();run.status=run.isCancellationRequested()?"CANCELLED":run.error==null?"SUCCEEDED":"FAILED";
        }
    }
}
