package org.example;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.*;

/** Opt-in real Selenium/CDP smoke test. Its only destination is a localhost fixture. */
@EnabledIfSystemProperty(named="localBrowserTest", matches="true")
class LocalBrowserTest {
    @ParameterizedTest @ValueSource(strings={"hidden","aria-hidden","disabled","iframe","normal"})
    void localOnlyJourneyAndActualBrowserNetwork(String layout) throws Exception {
        AtomicInteger send=new AtomicInteger(), submit=new AtomicInteger(), finalSubmit=new AtomicInteger(), proceeds=new AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean popupScreenshotBeforeProceed=new java.util.concurrent.atomic.AtomicBoolean();
        RunRecord run=new RunRecord();
        java.util.concurrent.atomic.AtomicReference<Map<String,String>> bankValues=new java.util.concurrent.atomic.AtomicReference<>();
        HttpServer api=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        api.createContext("/bank",exchange->{
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin","*");
            byte[] body="{}".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        }); api.start();
        HttpServer page=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String html="""
            <!doctype html><html><body>
            <div id="first"><input id="mobile_number"><select id="select_dependancy"><option>Fixture dependency</option></select><input id="name">
            <button id="SaveData" onclick="sendOtp()">SEND OTP</button></div>
            <section id="otpPage" hidden><input id="mobile_otp"><button id="otpSubmit" onclick="submitOtp()">Submit</button></section>
            <section id="bankPage" hidden><input id="ifsc" onblur="populate()"><input id="micr1" disabled><input id="bank_address" disabled>
            <input id="account_number"><input id="reenter_account_number"><select id="account_type" onchange="recordBank()"><option>Choose</option><option>Fixture account</option></select>
            <button id="SaveData" onclick="bankSubmit()">Verify bank</button></section>
            <section id="bankPopUp" hidden><h2>Fixture bank popup</h2><p>Fixture verification outcome</p><button onclick="popUp_save('true','bankPopUp');this.disabled = true;">Proceed</button></section>
            <script>
            async function sendOtp(){await fetch('/send'); document.querySelector('#first').hidden=true;document.querySelector('#otpPage').hidden=false;}
            async function submitOtp(){await fetch('/submit');document.querySelector('#otpPage').hidden=true;let bank=document.querySelector('#bankPage');if(bank.tagName==='TEMPLATE'){const section=document.createElement('section');section.id='bankPage';section.innerHTML=bank.innerHTML;bank.replaceWith(section);bank=section;}bank.hidden=false;bank.removeAttribute('aria-hidden');bank.querySelectorAll('[data-inactive]').forEach(e=>e.disabled=false);}
            async function populate(){await fetch('http://127.0.0.1:%d/bank'); document.querySelector('#micr1').value='fixture-micr';document.querySelector('#bank_address').value='Fixture address';fetch('/late');}
            function recordBank(){const value=id=>document.getElementById(id).value;fetch('/record',{method:'POST',body:JSON.stringify({ifsc:value('ifsc'),account:value('account_number'),confirmation:value('reenter_account_number'),type:value('account_type')})});}
            async function bankSubmit(){await fetch('/bank-submit');setTimeout(()=>document.getElementById('bankPopUp').hidden=false,350);}
            async function popUp_save(action,id){await fetch('/proceed');window.location.href='/result';}
            </script></body></html>
            """.formatted(api.getAddress().getPort());
        if(layout.equals("iframe"))html=html.replace("<section id=\"otpPage\" hidden><input id=\"mobile_otp\"><button id=\"otpSubmit\" onclick=\"submitOtp()\">Submit</button></section>","<iframe id=\"otpPage\" src=\"/otp-frame\" hidden></iframe>");
        if(layout.equals("aria-hidden"))html=html.replace("<section id=\"bankPage\" hidden>","<section id=\"bankPage\" aria-hidden=\"true\">");
        if(layout.equals("disabled"))html=html.replace("<section id=\"bankPage\" hidden>","<section id=\"bankPage\">")
                .replace("<input id=\"ifsc\"","<input disabled data-inactive id=\"ifsc\"").replace("<input id=\"account_number\"","<input disabled data-inactive id=\"account_number\"")
                .replace("<input id=\"reenter_account_number\"","<input disabled data-inactive id=\"reenter_account_number\"").replace("<select id=\"account_type\"","<select disabled data-inactive id=\"account_type\"");
        if(layout.equals("normal"))html=html.replace("<section id=\"bankPage\" hidden>","<template id=\"bankPage\">").replace("Verify bank</button></section>","Verify bank</button></template>");
        final String fixtureHtml=html;
        page.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath();
            int code=200;
            if(path.equals("/send"))send.incrementAndGet();
            if(path.equals("/submit"))submit.incrementAndGet();
            if(path.equals("/bank-submit"))finalSubmit.incrementAndGet();
            if(path.equals("/proceed")){proceeds.incrementAndGet();popupScreenshotBeforeProceed.set(java.nio.file.Files.isRegularFile(RunScreenshots.path(run.id,"bank-popup")));}
            if(path.equals("/record")) {
                @SuppressWarnings("unchecked") Map<String,String> values=new com.fasterxml.jackson.databind.ObjectMapper().readValue(exchange.getRequestBody(),Map.class);
                bankValues.set(values);
            }
            if(path.equals("/late")){try{Thread.sleep(500);}catch(InterruptedException ignored){}code=202;}
            String body=path.equals("/")?fixtureHtml:path.equals("/result")?"<html><body><h1>Fixture resulting page</h1><script>fetch('/result-api')</script></body></html>":path.equals("/otp-frame")?"<input id='mobile_otp'><button id='otpSubmit' onclick='parent.submitOtp()'>Submit</button>":"{}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type",path.equals("/") || path.equals("/otp-frame") || path.equals("/result")?"text/html":"application/json");
            exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });page.start();
        try {
            PortalSettings settings=new PortalSettings("151",15,5,null,new PortalSettings.Locators("//button[@id='otpSubmit']","#micr1","#bank_address"));
            RunInput input=new RunInput("6999999999","Fixture dependency","QA Fixture","TEST0000000","0000123","0000123","Fixture account",RunInput.OtpMode.FIXED,"0007",RunInput.BrowserMode.HEADLESS);
            new KycJourney(settings).executeAt(input,run,"http://127.0.0.1:"+page.getAddress().getPort()+"/");
            assertThat(run.status).withFailMessage("%s",run.error).isEqualTo("SUCCEEDED");
            assertThat(run.workflowStage).isEqualTo(WorkflowStage.COMPLETED);assertThat(run.otpEntered).isTrue();assertThat(run.otpSubmitted).isTrue();
            assertThat(bankValues.get()).containsEntry("ifsc",input.ifsc()).containsEntry("account",input.accountNumber()).containsEntry("confirmation",input.confirmAccountNumber()).containsEntry("type",input.accountType());
            var stages=((java.util.List<Map<String,String>>)run.view().get("logs")).stream().map(l->l.get("workflowStage")).distinct().toList();
            assertThat(stages).containsExactly("APPLICANT_DETAILS","WAITING_FOR_OTP","ENTERING_OTP","WAITING_FOR_BANK","FILLING_BANK","SUBMITTING_BANK","WAITING_FOR_BANK_POPUP","CONFIRMING_BANK_POPUP","WAITING_FOR_RESULT","COMPLETED");
            assertThat(run.view().get("diagnostics").toString()).contains("otpElements","ifscElements","container","screenshot");
            @SuppressWarnings("unchecked") var diagnostics=(Map<String,Object>)run.view().get("diagnostics");
            @SuppressWarnings("unchecked") var otpPage=(Map<String,Object>)diagnostics.get("otpPage");
            if(!layout.equals("iframe")&&!layout.equals("normal")) {
                assertThat(otpPage.get("otpInputCount")).isEqualTo(1);assertThat(otpPage.get("ifscInputCount")).isEqualTo(1);
                @SuppressWarnings("unchecked") var ifsc=((java.util.List<Map<String,Object>>)otpPage.get("ifscElements")).get(0);
                assertThat(ifsc.get("active")).isEqualTo(false);
                if(layout.equals("hidden"))assertThat(ifsc.get("visible")).isEqualTo(false);
                if(layout.equals("aria-hidden"))assertThat(ifsc.get("visible")).isEqualTo(true); // Old isDisplayed-only guard falsely rejected this.
                if(layout.equals("disabled")){assertThat(ifsc.get("visible")).isEqualTo(true);assertThat(ifsc.get("enabled")).isEqualTo(false);}
            }
            assertThat(java.nio.file.Files.isRegularFile(KycJourney.screenshotPath(run.id))).isTrue();
            assertThat(send.get()).isEqualTo(1);assertThat(submit.get()).isEqualTo(1);assertThat(finalSubmit.get()).isEqualTo(1);assertThat(proceeds.get()).isEqualTo(1);assertThat(popupScreenshotBeforeProceed.get()).isTrue();
            assertThat(java.nio.file.Files.isRegularFile(RunScreenshots.path(run.id,"bank-popup"))).isTrue();assertThat(java.nio.file.Files.isRegularFile(RunScreenshots.path(run.id,"result-page"))).isTrue();
            assertThat(run.view().get("screenshots").toString()).contains("bank-popup","result-page");
            assertThat(diagnostics.get("resultPage").toString()).contains("/result");
            var responses=run.network.snapshot();
            assertThat(responses).anyMatch(r->r.url().endsWith("/bank") && Integer.valueOf(200).equals(r.status()));
            assertThat(responses).anyMatch(r->r.url().endsWith("/late") && Integer.valueOf(202).equals(r.status()) && r.state().equals("COMPLETED"));
            assertThat(responses).allMatch(r->r.runId().equals(run.id));
            assertThat(responses).anyMatch(r->r.url().endsWith("/result-api")&&Integer.valueOf(200).equals(r.status()));
        } finally { page.stop(0);api.stop(0); }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void http200WithoutOtpFailsWithEvidenceAndNeverRetries(boolean applicationRejection) throws Exception {
        AtomicInteger sends=new AtomicInteger();
        HttpServer page=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        page.createContext("/",exchange->{
            boolean first=exchange.getRequestURI().getPath().equals("/");
            if(exchange.getRequestURI().getPath().equals("/send"))sends.incrementAndGet();
            String body=first?"<input id='mobile_number'><select id='select_dependancy'><option>Fixture dependency</option></select><input id='name'><button id='SaveData' onclick=\"fetch('/send')\">SEND OTP</button>":applicationRejection?"{\"success\":false,\"msg\":\"Fixture rejected 6999999999\"}":"{}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type",first?"text/html":"application/json");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });page.start();
        try {
            PortalSettings settings=new PortalSettings("151",3,2,null,new PortalSettings.Locators("//button[@id='otpSubmit']","#micr1","#bank_address"));
            RunInput input=new RunInput("6999999999","Fixture dependency","QA Fixture","TEST0000000","0000123","0000123","Fixture account",RunInput.OtpMode.FIXED,"0007",RunInput.BrowserMode.HEADLESS);
            RunRecord run=new RunRecord();new KycJourney(settings).executeAt(input,run,"http://127.0.0.1:"+page.getAddress().getPort()+"/");
            assertThat(run.status).isEqualTo("FAILED");
            assertThat(sends.get()).isEqualTo(1);
            assertThat(run.error).contains(applicationRejection?"Send OTP was rejected":"did not produce a visible");
            assertThat(run.error).doesNotContain("6999999999");
            assertThat(run.view().get("diagnostics").toString()).contains("failurePage","visibleFields").doesNotContain("6999999999","0007","QA Fixture");
        } finally {page.stop(0);}
    }
    @ParameterizedTest @ValueSource(strings={"direct","cancel"})
    void unexpectedDirectBankAndCancellationNeverSubmitOtpOrBank(String scenario) throws Exception {
        AtomicInteger sends=new AtomicInteger(),submits=new AtomicInteger(),bankCalls=new AtomicInteger();
        HttpServer page=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String afterSend=scenario.equals("direct")?"document.getElementById('first').hidden=true;document.getElementById('bankPage').hidden=false;":"";
        String html="<section id='first'><input id='mobile_number'><select id='select_dependancy'><option>Fixture dependency</option></select><input id='name'><button id='SaveData' onclick='send()'>SEND OTP</button></section><section id='otpPage' hidden><input id='mobile_otp'></section><section id='bankPage' hidden><input id='ifsc'><input id='account_number'><input id='reenter_account_number'><select id='account_type'><option>Fixture account</option></select></section><script>async function send(){await fetch('/send');"+afterSend+"}</script>";
        page.createContext("/",exchange->{String path=exchange.getRequestURI().getPath();if(path.equals("/send"))sends.incrementAndGet();if(path.equals("/submit"))submits.incrementAndGet();if(path.equals("/bank"))bankCalls.incrementAndGet();byte[] body=(path.equals("/")?html:"{}").getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type",path.equals("/")?"text/html":"application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});page.start();
        var worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var settings=new PortalSettings("151",15,2,null,new PortalSettings.Locators("//button[@id='otpSubmit']","#micr1","#bank_address"));
            var input=new RunInput("6999999999","Fixture dependency","QA Fixture","TEST0000000","0000123","0000123","Fixture account",RunInput.OtpMode.FIXED,"0007",RunInput.BrowserMode.HEADLESS);
            RunRecord run=new RunRecord();var future=worker.submit(()->new KycJourney(settings).executeAt(input,run,"http://127.0.0.1:"+page.getAddress().getPort()+"/"));
            if(scenario.equals("cancel")) {
                long deadline=System.nanoTime()+java.time.Duration.ofSeconds(30).toNanos();
                while(System.nanoTime()<deadline&&(sends.get()==0||run.workflowStage!=WorkflowStage.WAITING_FOR_OTP))Thread.sleep(100);
                assertThat(sends.get()).isEqualTo(1);run.requestCancel();
            }
            future.get(45,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(run.status).isEqualTo(scenario.equals("cancel")?"CANCELLED":"FAILED");
            assertThat(run.workflowStage).isEqualTo(WorkflowStage.WAITING_FOR_OTP);assertThat(run.otpEntered).isFalse();assertThat(run.otpSubmitted).isFalse();
            assertThat(sends.get()).isEqualTo(1);assertThat(submits.get()).isZero();assertThat(bankCalls.get()).isZero();
            if(scenario.equals("direct"))assertThat(run.error).contains("Unexpected direct bank-page transition").doesNotContain("bypass");
            assertThat(run.view().get("diagnostics").toString()).contains("otpElements","ifscElements","screenshot");
        } finally {worker.shutdownNow();page.stop(0);}
    }
}
