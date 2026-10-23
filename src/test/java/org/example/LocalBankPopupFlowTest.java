package org.example;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.WebDriverWait;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named="localBrowserTest",matches="true")
class LocalBankPopupFlowTest {
    @ParameterizedTest @ValueSource(strings={"same-url","missing-popup","failed-navigation"})
    void popupAndNavigationWaitsAreRequiredAndNeverRetryClicks(String scenario)throws Exception {
        AtomicInteger submits=new AtomicInteger(),proceeds=new AtomicInteger();AtomicBoolean done=new AtomicBoolean(),popupSaved=new AtomicBoolean(),beforeProceedSaved=new AtomicBoolean();
        RunRecord run=new RunRecord();run.workflowStage=WorkflowStage.FILLING_BANK;run.otpEntered=true;run.otpSubmitted=true;
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String show=scenario.equals("missing-popup")?"":"setTimeout(()=>document.getElementById('bankPopUp').hidden=false,250);";
        String after=scenario.equals("same-url")?"window.location.reload();":"document.getElementById('bankPopUp').hidden=true;";
        String html="<section id='bank'><input id='ifsc'><input id='account_number'><input id='reenter_account_number'><select id='account_type'><option>Fixture</option></select><button id='SaveData' onclick='submitBank()'>Submit bank</button></section><section id='bankPopUp' hidden><h2>Local popup</h2><button onclick=\"popUp_save('true','bankPopUp');this.disabled = true;\">Proceed</button></section><script>async function submitBank(){await fetch('/submit-bank');"+show+"}async function popUp_save(a,b){await fetch('/proceed');"+after+"}</script>";
        server.createContext("/",exchange->{String path=exchange.getRequestURI().getPath();if(path.equals("/submit-bank"))submits.incrementAndGet();if(path.equals("/proceed")){proceeds.incrementAndGet();popupSaved.set(Files.isRegularFile(RunScreenshots.path(run.id,"bank-popup")));beforeProceedSaved.set(Files.isRegularFile(RunScreenshots.path(run.id,"bank-popup-before-proceed")));done.set(scenario.equals("same-url"));}String body=path.equals("/")?(done.get()?"<html><body><h1>Local resulting page</h1></body></html>":html):"{}";byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type",path.equals("/")?"text/html":"application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        ChromeOptions options=new ChromeOptions();options.setBrowserVersion("151");options.addArguments("--headless=new","--window-size=1440,1000");
        if(System.getProperty("localChromeBinary")!=null)options.setBinary(System.getProperty("localChromeBinary"));
        ChromeDriver driver=new ChromeDriver(options);
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort()+"/";driver.get(url);
            WebDriverWait wait=new WebDriverWait(driver,Duration.ofSeconds(3));wait.ignoring(StaleElementReferenceException.class);
            if(scenario.equals("same-url")){BankPopupFlow.complete(driver,wait,run);assertThat(driver.getCurrentUrl()).isEqualTo(url);assertThat(Files.isRegularFile(RunScreenshots.path(run.id,"result-page"))).isTrue();}
            else assertThatThrownBy(()->BankPopupFlow.complete(driver,wait,run)).isInstanceOf(TimeoutException.class);
            assertThat(submits.get()).isEqualTo(1);assertThat(proceeds.get()).isEqualTo(scenario.equals("missing-popup")?0:1);
            if(!scenario.equals("missing-popup")){assertThat(popupSaved.get()).isTrue();assertThat(beforeProceedSaved.get()).isTrue();assertThat(Files.isRegularFile(RunScreenshots.path(run.id,"bank-popup"))).isTrue();assertThat(run.view().get("screenshots").toString()).contains("bank-popup-before-proceed");}
            if(scenario.equals("failed-navigation"))assertThat(Files.isRegularFile(RunScreenshots.path(run.id,"result-page"))).isFalse();
        }finally{driver.quit();server.stop(0);}
    }
}
