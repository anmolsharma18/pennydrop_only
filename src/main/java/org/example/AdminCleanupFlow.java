package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

/** One run-owned row only. Browser-produced responses are used for correlation, never standalone API calls. */
final class AdminCleanupFlow {
    static final By USER=By.xpath("//input[@id='username-input']"),PASSWORD=By.xpath("//input[@id='password-input']");
    static final By LOGIN=By.id("login-submit-button"),MAKER=By.xpath("//a[@id='makerchecker__li']");
    static final By WORKFLOW=By.xpath("//button[@id='select__worflow_btn']"),WORKFLOW_LABEL=By.xpath("//label[normalize-space()='PENNYDROP ONLY']");
    static final By STATUS=By.xpath("//button[@id='select__Status_btn']");
    // Supplied positional icon is verified against #admin (Verify); use its associated visible label when unchecked.
    static final By SUPPLIED_VERIFY_ICON=By.xpath("//div[@class='filter_sections']//li[2]//label[1]//i[1]");
    static final By VERIFY_LABEL=By.cssSelector("#select__Status_li label[for='admin']");
    static final By MENU=By.xpath(".//span[@class='mdi mdi-dots-vertical cursor-pointer']");
    // Same supplied Delete XPath, relative to the matched row rather than an unrelated first table row.
    static final By DELETE=By.xpath("(.//div[normalize-space()='Delete'])[1]");
    static final By REASON=By.xpath("(//select[@id='deleteReasonSelect'])[1]");
    static final By CONFIRM=By.xpath("//button[@id='confirmDeleteBtn']");
    static final String DELETE_REASON="Duplicate entry";
    static final By SUCCESS=By.id("success_model"),FAILURE=By.id("failure_model");
    static final By TOUR_END=AdminTourDismissal.END;
    static final By TOUR_VISIBLE=AdminTourDismissal.VISIBLE;
    private static final Pattern DELETE_CONTEXT=Pattern.compile("^\\s*deleteUser\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*,");

    static void complete(WebDriver d,WebDriverWait wait,RunRecord run,RunInput input,String identity,BrowserNetwork monitor,AdminCredentials credentials,String url)throws Exception {
        complete(d,wait,run,input,identity,monitor,credentials,url,Duration.ofSeconds(10),Duration.ofSeconds(2));
    }
    static void complete(WebDriver d,WebDriverWait wait,RunRecord run,RunInput input,String journeyIdentity,BrowserNetwork monitor,AdminCredentials credentials,String url,Duration gap,Duration afterClick)throws Exception {
        if(run.workflowStage!=WorkflowStage.WAITING_FOR_RESULT||!run.otpSubmitted||!java.nio.file.Files.isRegularFile(RunScreenshots.path(run.id,"result-page")))throw new IllegalStateException("Admin cleanup requires completed penny-drop result capture after OTP");
        run.diagnostic("pennyDropCaptured",true);
        run.transition(WorkflowStage.WAITING_FOR_ADMIN_CLEANUP,"Penny-drop result captured; waiting 10 seconds before admin cleanup");pause(gap,run);
        deleteMatched(d,wait,run,DeleteInput.from(input),journeyIdentity,monitor,credentials,url,afterClick);
    }
    static void deleteOnly(WebDriver d,WebDriverWait wait,RunRecord run,DeleteInput input,BrowserNetwork monitor,AdminCredentials credentials,String url)throws Exception {
        if(run.kind!=RunRecord.Kind.DELETE_ONLY||run.workflowStage!=WorkflowStage.ADMIN_LOGIN||run.otpEntered||run.otpSubmitted)throw new IllegalStateException("Standalone deletion requires a fresh DELETE_ONLY run");
        deleteMatched(d,wait,run,input,null,monitor,credentials,url,Duration.ofSeconds(2));
    }
    private static void deleteMatched(WebDriver d,WebDriverWait wait,RunRecord run,DeleteInput input,String journeyIdentity,BrowserNetwork monitor,AdminCredentials credentials,String url,Duration afterClick)throws Exception {
        AdminTourDismissal tour=new AdminTourDismissal();
        Set<String> beforeAdmin=new HashSet<>();run.network.snapshot().forEach(r->beforeAdmin.add(r.requestId()));
        run.transition(WorkflowStage.ADMIN_LOGIN,"Opening admin portal and signing in for cleanup");d.switchTo().defaultContent();d.get(url);run.checkCancelled();
        PageActions.fill(d,wait,USER,credentials.username(),run);PageActions.fill(d,wait,PASSWORD,credentials.password(),run);
        WebElement login=clickable(d,wait,LOGIN,run,tour);run.stage("Admin Login click (once)");run.checkCancelled();login.click();
        clickable(d,wait,MAKER,run,tour).click();
        run.transition(WorkflowStage.FILTERING_MAKER_CHECKER,"Selecting PENNYDROP ONLY workflow and Verify status");
        clickable(d,wait,WORKFLOW,run,tour).click();
        WebElement label=clickable(d,wait,WORKFLOW_LABEL,run,tour);String forId=label.getDomAttribute("for");
        if(forId==null||forId.isBlank())throw new IllegalStateException("Workflow label has no associated checkbox");
        WebElement checkbox=d.findElement(By.id(forId));if(!"pennydrop_only".equals(checkbox.getDomAttribute("value")))throw new IllegalStateException("Workflow label is not associated with pennydrop_only");
        if(!checkbox.isSelected()){run.checkCancelled();label.click();}
        wait.until(x->x.findElement(By.id(forId)).isSelected());
        if(JourneyPage.visible(d,By.id("select__workflow_list")))clickable(d,wait,WORKFLOW,run,tour).click();
        clickable(d,wait,STATUS,run,tour).click();
        WebElement verify=d.findElement(By.cssSelector("#select__Status_li input#admin"));
        if(!"Verify".equals(verify.getDomAttribute("value")))throw new IllegalStateException("Verify checkbox changed; no entry will be deleted");
        for(WebElement selected:d.findElements(By.cssSelector("#select__Status_li input[type=checkbox]"))) {
            String id=selected.getDomAttribute("id");if(!"admin".equals(id)&&selected.isSelected()){
                WebElement associated=d.findElement(By.cssSelector("#select__Status_li label[for='"+id+"']"));run.checkCancelled();associated.click();
            }
        }
        if(!verify.isSelected()) {
            List<WebElement> icons=d.findElements(SUPPLIED_VERIFY_ICON);
            if(icons.size()==1&&icons.get(0).isDisplayed()&&"admin".equals(icons.get(0).findElement(By.xpath("..//input")).getDomAttribute("id"))){run.checkCancelled();icons.get(0).click();}
            else{run.checkCancelled();clickable(d,wait,VERIFY_LABEL,run,tour).click();}
        }
        wait.until(x->x.findElement(By.cssSelector("#select__Status_li input#admin")).isSelected());
        if(JourneyPage.visible(d,By.id("select__Status_list")))clickable(d,wait,STATUS,run,tour).click();
        run.transition(WorkflowStage.MATCHING_ADMIN_ENTRY,"Searching for this run's exact applicant/journey entry");
        Set<String> beforeSearch=new HashSet<>(beforeAdmin);
        if(JourneyPage.activeField(d,By.id("makerCheckerSearch"))!=null){
            run.network.snapshot().stream().filter(r->tableRequest(r.url())).forEach(r->beforeSearch.add(r.requestId()));
            clickable(d,wait,By.id("makerCheckerSearch"),run,tour);
            PageActions.fill(d,wait,By.id("makerCheckerSearch"),input.mobile(),run);
            run.diagnostic("adminMatchingSource","Visible search control and browser-produced search responses");
        }else{
            run.stage("Legacy search control inactive; matching the loaded maker-checker table before opening actions");
            run.diagnostic("adminMatchingSource","Loaded admin table responses; exact match and result completeness required");
        }
        String identity=wait.until(x->{
            run.checkCancelled();tour.dismissIfVisible(x,run);if(JourneyPage.visible(x,TOUR_VISIBLE))return null;failIfAdminError(x);
            List<NetworkLedger.Entry> responses=run.network.snapshot();
            for(int i=responses.size()-1;i>=0;i--){var response=responses.get(i);
                if(!tableRequest(response.url())||beforeSearch.contains(response.requestId())||!response.state().equals("COMPLETED"))continue;
                if(response.status()==null||response.status()<200||response.status()>=300)throw new IllegalStateException("Maker-checker search failed (HTTP "+response.status()+")");
                JsonNode json=monitor.responseJson(response.requestId());String match=AdminEntryMatcher.match(json,input,journeyIdentity);
                if(json!=null)run.diagnostic("adminSearch",Map.of("requestId",response.requestId(),"httpStatus",response.status(),"returnedRecords",json.path("data").size(),"totalRecords",json.path("total_counts").asInt(-1),"matched",match!=null));
                if(match==null)return null;return deleteRows(x,match).size()==1?match:null;
            }return null;
        });
        run.diagnostic("adminCleanup",Map.of("entryFingerprint",AdminEntryMatcher.fingerprint(identity),"matchMethod",journeyIdentity==null?"unique exact mobile/name/account/IFSC in complete search result":"run-owned journey identity","reason",DELETE_REASON,"deleteSubmitted",false));
        WebElement row=uniqueRow(d,identity);RunScreenshots.capture(d,run,"admin-matched-entry");
        run.transition(WorkflowStage.OPENING_DELETE_DIALOG,"Opening matched entry action menu and Delete dialog");
        WebElement menu=wait.until(x->{run.checkCancelled();tour.dismissIfVisible(x,run);if(JourneyPage.visible(x,TOUR_VISIBLE))return null;
            var menus=uniqueRow(x,identity).findElements(MENU).stream().filter(e->e.isDisplayed()&&e.isEnabled()).toList();
            if(menus.size()>1)throw new IllegalStateException("Matched row has ambiguous action menus");return menus.isEmpty()?null:menus.get(0);});
        run.checkCancelled();run.stage("Matched entry three-dot action menu click (once)");menu.click();
        WebElement delete=wait.until(x->{run.checkCancelled();tour.dismissIfVisible(x,run);if(JourneyPage.visible(x,TOUR_VISIBLE))return null;return visibleDelete(uniqueRow(x,identity),identity);});
        run.checkCancelled();run.stage("Matched entry Delete menu item click (once)");delete.click();
        run.transition(WorkflowStage.CONFIRMING_ADMIN_DELETE,"Selecting Duplicate entry reason for the matched entry");
        clickable(d,wait,REASON,run,tour);
        PageActions.select(d,wait,REASON,DELETE_REASON,run);
        WebElement confirm=clickable(d,wait,CONFIRM,run,tour);
        String context=String.valueOf(((JavascriptExecutor)d).executeScript("return typeof pendingDeleteContext!=='undefined' && pendingDeleteContext ? pendingDeleteContext.identity : null;"));
        if(!identity.equals(context))throw new IllegalStateException("Delete dialog does not belong to the matched journey; stopped before confirmation");
        if(JourneyPage.visible(d,SUCCESS)||JourneyPage.visible(d,FAILURE))throw new IllegalStateException("A previous admin outcome popup is still visible; stopped before deletion");
        RunScreenshots.capture(d,run,"admin-delete-confirmation");
        Set<String> beforeDelete=new HashSet<>();run.network.snapshot().forEach(r->beforeDelete.add(r.requestId()));
        run.checkCancelled();run.stage("Admin Confirm Delete click (once)");run.diagnostic("adminDeleteAttempted",true);confirm.click();
        run.diagnostic("adminCleanup",Map.of("entryFingerprint",AdminEntryMatcher.fingerprint(identity),"matchMethod",journeyIdentity==null?"unique exact applicant/bank match":"run-owned journey identity","reason",DELETE_REASON,"deleteSubmitted",true));
        run.transition(WorkflowStage.WAITING_FOR_DELETE_SUCCESS,"Deletion submitted once; waiting 2 seconds then capturing success popup");pause(afterClick,run);
        wait.until(x->{
            run.checkCancelled();failIfAdminError(x);
            var response=run.network.snapshot().stream().filter(r->!beforeDelete.contains(r.requestId())&&deleteRequest(r.url(),identity)&&r.state().equals("COMPLETED")).findFirst().orElse(null);
            if(response==null)return false;
            Map<String,Object> feedback=monitor.applicationFeedback(response.requestId());var outcome=new LinkedHashMap<String,Object>(feedback);outcome.put("requestId",response.requestId());outcome.put("httpStatus",response.status()==null?"no response":response.status());run.diagnostic("adminDeleteResponse",outcome);
            if(response.status()==null||response.status()<200||response.status()>=300||!Boolean.TRUE.equals(feedback.get("success")))throw new IllegalStateException("Admin deletion was not confirmed by the actual browser response (HTTP "+response.status()+"). No delete retry.");
            return JourneyPage.visible(x,SUCCESS);
        });
        RunScreenshots.capture(d,run,"admin-delete-success");
        run.diagnostic("adminCleanupCompleted",true);run.stage("Admin deletion success popup captured; network monitoring remains active until cleanup");
    }
    private static boolean tableRequest(String url){return java.net.URI.create(url).getPath().equals("/get_user_data_v2");}
    private static boolean deleteRequest(String url,String identity){return java.net.URI.create(url).getPath().equals("/delete_user/"+identity);}
    static void pause(Duration duration,RunRecord run)throws InterruptedException {long end=System.nanoTime()+duration.toNanos();while(System.nanoTime()<end){run.checkCancelled();Thread.sleep(Math.min(100,Math.max(1,(end-System.nanoTime())/1_000_000)));}run.checkCancelled();}
    private static WebElement clickable(WebDriver d,WebDriverWait wait,By locator,RunRecord run,AdminTourDismissal tour){return wait.until(x->{run.checkCancelled();tour.dismissIfVisible(x,run);if(JourneyPage.visible(x,TOUR_VISIBLE))return null;if(x.findElements(By.cssSelector(".overlay-bg,.overlay-bg-meon,.guided-tour-step-highlight,.guided-tour-step-tooltip")).stream().anyMatch(WebElement::isDisplayed))return null;return JourneyPage.activeField(x,locator);});}
    private static void failIfAdminError(WebDriver d){if(JourneyPage.visible(d,FAILURE))throw new IllegalStateException("Admin action failed: "+JourneyPage.safeText(d.findElement(By.id("failedMessage")).getText()));}
    private static List<WebElement> deleteRows(WebDriver d,String identity){
        List<WebElement> rows=new ArrayList<>();for(WebElement row:d.findElements(By.cssSelector("#tbody_maker_checker tr"))){
            if(!row.isDisplayed())continue;for(WebElement e:row.findElements(By.cssSelector("[onclick]"))){Matcher m=DELETE_CONTEXT.matcher(Objects.toString(e.getDomAttribute("onclick"),""));if(m.find()&&identity.equals(m.group(1))&&"pennydrop_only".equals(m.group(2))&&"Delete".equals(e.getDomProperty("textContent").trim())){rows.add(row);break;}}
        }return rows;
    }
    private static WebElement uniqueRow(WebDriver d,String identity){List<WebElement> rows=deleteRows(d,identity);if(rows.size()!=1)throw new IllegalStateException("Matched admin row is missing or duplicated; no further action taken");return rows.get(0);}
    private static WebElement visibleDelete(WebElement row,String identity){for(WebElement e:row.findElements(DELETE)){Matcher m=DELETE_CONTEXT.matcher(Objects.toString(e.getDomAttribute("onclick"),""));if(m.find()&&identity.equals(m.group(1))&&"pennydrop_only".equals(m.group(2))&&e.isDisplayed()&&e.isEnabled())return e;}return null;}
}


