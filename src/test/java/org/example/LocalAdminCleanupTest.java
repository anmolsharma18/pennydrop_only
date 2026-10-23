package org.example;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.WebDriverWait;
import static org.assertj.core.api.Assertions.*;

/** Real Chrome/CDP, exclusively localhost. Fixture credentials and records; never live admin login/deletion. */
@EnabledIfSystemProperty(named="localBrowserTest",matches="true")
class LocalAdminCleanupTest {
    @org.junit.jupiter.api.io.TempDir Path credentialsDirectory;
    @ParameterizedTest @ValueSource(strings={"normal","already-selected","tour","tour-before-maker","tooltip-only","ambiguous","incomplete","failed","wrong-dialog","standalone","hidden-search","missing-reason"})
    void localAdminFlowUsesOnlyMatchingEntryAndCapturesActualResponses(String mode)throws Exception {
        AtomicInteger logins=new AtomicInteger(),deletes=new AtomicInteger();AtomicBoolean screenshotBeforeDelete=new AtomicBoolean();AtomicLong clickedAt=new AtomicLong();
        RunRecord run=new RunRecord(mode.equals("standalone")?RunRecord.Kind.DELETE_ONLY:RunRecord.Kind.JOURNEY);
        if(!mode.equals("standalone")){run.workflowStage=WorkflowStage.WAITING_FOR_RESULT;run.otpEntered=true;run.otpSubmitted=true;}
        RunInput input=new RunInput("6999999999","Fixture","QA Fixture","TEST0000000","0000123","0000123","Fixture",RunInput.OtpMode.FIXED,"0007",RunInput.BrowserMode.HEADLESS);
        String matching="{\"identity\":\"fixture-entry\",\"workflowName\":\"pennydrop_only\",\"mobile_number\":\"6999999999\",\"name\":\"QA Fixture\",\"account_number\":\"0000123\",\"ifsc\":\"TEST0000000\"}";
        String other=matching.replace("fixture-entry","unrelated-entry").replace("QA Fixture","Other Fixture");
        String data="["+other+","+matching+(mode.equals("ambiguous")?","+matching.replace("fixture-entry","duplicate-entry"):"")+"]";
        String table="{\"total_counts\":"+(mode.equals("incomplete")?3:mode.equals("ambiguous")?3:2)+",\"data\":"+data+"}";
        String checked=mode.equals("already-selected")?"checked":"";
        String maker="""
            <html><body><div class="overlay-bg" style="display:none"></div><div class="filter_sections">
            <button id="select__worflow_btn" onclick="toggle('select__workflow_list')">Workflow</button><div id="select__workflow_list" hidden><input type="checkbox" id="fixture-workflow" value="pennydrop_only" %s><label for="fixture-workflow">PENNYDROP ONLY</label></div>
            <button id="select__Status_btn" onclick="toggle('select__Status_list')">Status</button><div id="select__Status_list" hidden><ul id="select__Status_li"><li><label><input type="checkbox" id="in_flow" value="In Progress"><i>Off</i></label><label for="in_flow">IN PROGRESS</label></li><li><label><input type="checkbox" id="admin" value="Verify" %s><i>On</i></label><label for="admin">VERIFY</label></li></ul></div></div>
            <input id="makerCheckerSearch" oninput="clearTimeout(timer);timer=setTimeout(loadRows,50)"><table><tbody id="tbody_maker_checker"></tbody></table>
            <div id="deleteModal" role="dialog" hidden><select id="deleteReasonSelect" onchange="document.getElementById('confirmDeleteBtn').disabled=!this.value"><option value="">Select</option><option value="Test or dummy record">Test or dummy record</option><option value="Duplicate entry">Duplicate entry</option></select><div class="modal-footer"><button>Cancel</button><button id="confirmDeleteBtn" disabled onclick="confirmDeleteFromModal()">Confirm Delete</button></div></div>
            <div id="success_model" hidden><h2 id="successMessage">Fixture deletion success</h2></div><div id="failure_model" hidden><h2 id="failedMessage">Fixture deletion failure</h2></div>
            <script>let pendingDeleteContext=null,timer;function toggle(id){document.getElementById(id).hidden=!document.getElementById(id).hidden;}
            async function loadRows(){const r=await fetch('/get_user_data_v2',{method:'POST',body:JSON.stringify({search:document.getElementById('makerCheckerSearch').value})});const json=await r.json();const body=document.getElementById('tbody_maker_checker');body.replaceChildren();for(const row of json.data){const tr=document.createElement('tr');tr.innerHTML=`<td>${row.name}</td><td><span class="mdi mdi-dots-vertical cursor-pointer" onclick="this.nextElementSibling.hidden=false">Menu</span><div hidden onclick="deleteUser('${row.identity}','${row.workflowName}','fixture-key','bank','fixture-row')">Delete</div></td>`;body.append(tr);}}
            function deleteUser(identity,workflow,key,position,rowId){pendingDeleteContext={identity:%s? 'wrong-entry':identity,rowId};document.getElementById('deleteModal').hidden=false;}
            async function confirmDeleteFromModal(){document.getElementById('confirmDeleteBtn').disabled=true;const r=await fetch('/delete_user/'+pendingDeleteContext.identity+'?delete_reason='+encodeURIComponent(document.getElementById('deleteReasonSelect').value));const data=await r.json();document.getElementById('deleteModal').hidden=true;document.getElementById(data.success?'success_model':'failure_model').hidden=false;fetch('/late-admin');}
            </script></body></html>
            """.formatted(checked,checked,mode.equals("wrong-dialog")?"true":"false");
        if(mode.equals("hidden-search"))maker=maker.replace("<input id=\"makerCheckerSearch\"", "<input hidden id=\"makerCheckerSearch\"").replace("</script>", "loadRows();</script>");
        if(mode.equals("missing-reason"))maker=maker.replace("<option value=\"Duplicate entry\">Duplicate entry</option>", "");
        String tourHtml="<div class='guided-tour-step-highlight'>Onboarding</div><div class='guided-tour-step-tooltip guided-tour-arrow-top'><span role='button' class='guided-tour-step-button-close' title='End tour' onclick=\"document.body.dataset.tourClicks=String(Number(document.body.dataset.tourClicks||0)+1);setTimeout(()=>{document.querySelectorAll('.guided-tour-step-highlight,.guided-tour-step-tooltip').forEach(e=>e.hidden=true);},200)\"><svg xmlns='http://www.w3.org/2000/svg' width='16' height='16' style='pointer-events:none'><rect width='16' height='16'/></svg></span></div>";
        if(mode.equals("tour")||mode.equals("tooltip-only"))maker=maker.replace("<body>","<body>"+(mode.equals("tooltip-only")?tourHtml.replace("<div class='guided-tour-step-highlight'>Onboarding</div>",""):tourHtml));
        final String makerHtml=maker;
        String login="""
            <html><body><input id="username-input"><input id="password-input" type="password"><button id="login-submit-button" onclick="login()">Login</button><a id="makerchecker__li" href="/maker" hidden>Maker checker</a><script>async function login(){await fetch('/login-api',{method:'POST',body:JSON.stringify({username:document.getElementById('username-input').value,password:document.getElementById('password-input').value})});document.getElementById('makerchecker__li').hidden=false;}</script></body></html>
            """;
        final String loginHtml=mode.equals("tour-before-maker")?login.replace("document.getElementById('makerchecker__li').hidden=false;","document.getElementById('makerchecker__li').hidden=false;document.body.insertAdjacentHTML('beforeend',"+new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tourHtml)+");"):login;
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{String path=exchange.getRequestURI().getPath();String body=path.equals("/admin")?loginHtml:path.equals("/maker")?makerHtml:path.equals("/get_user_data_v2")?table:path.startsWith("/delete_user/")?"{\"success\":"+!mode.equals("failed")+",\"msg\":\"Fixture result\"}":"{}";int status=200;
            if(path.equals("/login-api")){logins.incrementAndGet();String payload=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);assertThat(payload).contains("fixture-login","fixture-password");}
            if(path.startsWith("/delete_user/")){assertThat(java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery(),StandardCharsets.UTF_8)).isEqualTo("delete_reason=Duplicate entry");deletes.incrementAndGet();clickedAt.set(System.nanoTime());assertThat(path).isEqualTo("/delete_user/fixture-entry");screenshotBeforeDelete.set(Files.isRegularFile(RunScreenshots.path(run.id,"admin-delete-confirmation"))&&(mode.equals("standalone")||Files.isRegularFile(RunScreenshots.path(run.id,"result-page"))));}
            if(path.equals("/late-admin"))status=202;
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type",path.equals("/admin")||path.equals("/maker")?"text/html":"application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        if(mode.equals("standalone")) {
            try {
                AdminCredentials.save(credentialsDirectory,"fixture-login","fixture-password");
                PortalSettings settings=new PortalSettings("151",12,3,null,null);
                new AdminDeleteJourney(settings,new AdminCleanupProperties(false,"http://127.0.0.1:"+server.getAddress().getPort()+"/admin",credentialsDirectory.toString())).execute(DeleteInput.from(input),run);
                assertThat(run.status).withFailMessage("%s",run.error).isEqualTo("SUCCEEDED");assertThat(run.workflowStage).isEqualTo(WorkflowStage.COMPLETED);
                assertThat(run.otpEntered).isFalse();assertThat(run.otpSubmitted).isFalse();assertThat(logins).hasValue(1);assertThat(deletes).hasValue(1);assertThat(screenshotBeforeDelete).isTrue();
                assertThat(Files.isRegularFile(RunScreenshots.path(run.id,"result-page"))).isFalse();assertThat(Files.size(RunScreenshots.path(run.id,"admin-delete-success"))).isGreaterThan(100);
                assertThat(run.network.snapshot()).anyMatch(r->r.url().endsWith("/delete_user/fixture-entry")&&Integer.valueOf(200).equals(r.status()));
                assertThat(((java.util.List<?>)run.view().get("logs")).toString()).doesNotContain("Penny-drop result captured","Sending OTP");
            }finally{server.stop(0);}return;
        }
        ChromeOptions options=new ChromeOptions();options.setBrowserVersion("151");options.addArguments("--headless=new","--window-size=1440,1000");if(System.getProperty("localChromeBinary")!=null)options.setBinary(System.getProperty("localChromeBinary"));ChromeDriver driver=new ChromeDriver(options);
        try(BrowserNetwork network=new BrowserNetwork(driver,run.network)) {
            driver.get("http://127.0.0.1:"+server.getAddress().getPort()+"/result");RunScreenshots.capture(driver,run,"result-page");
            Duration gap=mode.equals("normal")?Duration.ofSeconds(10):Duration.ZERO;Duration after=mode.equals("normal")?Duration.ofSeconds(2):Duration.ofMillis(100);
            var wait=new WebDriverWait(driver,Duration.ofSeconds(12));wait.ignoring(StaleElementReferenceException.class);
            long before=System.nanoTime();Throwable failure=null;
            try{AdminCleanupFlow.complete(driver,wait,run,input,null,network,new AdminCredentials("fixture-login","fixture-password"),"http://127.0.0.1:"+server.getAddress().getPort()+"/admin",gap,after);}catch(Exception e){failure=e;}
            if(mode.equals("normal")||mode.equals("already-selected")||mode.equals("tour")||mode.equals("tour-before-maker")||mode.equals("tooltip-only")||mode.equals("hidden-search")) {
                assertThat(failure).isNull();assertThat(deletes).hasValue(1);assertThat(screenshotBeforeDelete).isTrue();assertThat(Files.size(RunScreenshots.path(run.id,"admin-delete-success"))).isGreaterThan(100);
                if(mode.equals("normal")){assertThat(Duration.ofNanos(clickedAt.get()-before)).isGreaterThanOrEqualTo(Duration.ofSeconds(10));assertThat(Duration.ofNanos(System.nanoTime()-clickedAt.get())).isGreaterThanOrEqualTo(Duration.ofSeconds(2));}
                assertThat(((java.util.Map<?,?>)run.view().get("diagnostics")).get("adminCleanupCompleted")).isEqualTo(true);
                if(mode.equals("tour")||mode.equals("tour-before-maker")||mode.equals("tooltip-only")){
                    assertThat(((java.util.Map<?,?>)run.view().get("diagnostics")).get("adminTourDismissed")).isEqualTo(true);
                    assertThat(((java.util.Map<?,?>)run.view().get("diagnostics")).get("adminTourLocator")).isEqualTo("(//span[@title='End tour'])[1]");
                    assertThat(((java.util.List<?>)run.view().get("logs")).stream().filter(e->e.toString().contains("Ending admin onboarding tour")).count()).isEqualTo(1);
                }
            }else{assertThat(failure).isNotNull();if(mode.equals("missing-reason"))assertThat(failure).hasMessageContaining("Exact visible option label 'Duplicate entry' does not exist");assertThat(deletes).hasValue(mode.equals("failed")?1:0);assertThat(Files.isRegularFile(RunScreenshots.path(run.id,"result-page"))).isTrue();}
            assertThat(logins).hasValue(1);
            long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();while(System.nanoTime()<deadline&&!run.network.settled(200))Thread.sleep(50);run.network.freeze();
            assertThat(run.network.snapshot()).anyMatch(r->r.url().endsWith("/login-api")&&Integer.valueOf(200).equals(r.status())).anyMatch(r->r.url().endsWith("/get_user_data_v2")&&Integer.valueOf(200).equals(r.status()));
            if(deletes.get()>0)assertThat(run.network.snapshot()).anyMatch(r->r.url().endsWith("/delete_user/fixture-entry")&&Integer.valueOf(200).equals(r.status()));
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(run.view())).doesNotContain("fixture-password","fixture-login","6999999999","0000123");
        }finally{driver.quit();server.stop(0);}
    }
}


