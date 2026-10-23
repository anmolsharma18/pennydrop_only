package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class AdminEntryMatcherTest {
    final ObjectMapper json=new ObjectMapper();
    final RunInput input=new RunInput("6999999999","Fixture","QA Fixture","TEST0000000","0000123","0000123","Fixture",RunInput.OtpMode.FIXED,"0007",RunInput.BrowserMode.HEADLESS);
    final String row="""
        {"identity":"fixture-entry","workflowName":"pennydrop_only","mobile_number":"6999999999","name":"QA Fixture","ifsc":"TEST0000000","account_number":"0000123"}
        """;
    @Test void completeExactApplicantMatchPreservesLeadingZeroes()throws Exception {
        assertThat(AdminEntryMatcher.match(json.readTree("{\"total_counts\":1,\"data\":["+row+"]}"),input,null)).isEqualTo("fixture-entry");
        assertThat(AdminEntryMatcher.match(json.readTree("{\"total_counts\":1,\"data\":["+row.replace("0000123","123")+"]}"),input,null)).isNull();
    }
    @Test void rejectsAmbiguousAndIncompleteSearchResults()throws Exception {
        assertThatThrownBy(()->AdminEntryMatcher.match(json.readTree("{\"total_counts\":2,\"data\":["+row+","+row.replace("fixture-entry","other-entry")+"]}"),input,null)).hasMessageContaining("Ambiguous");
        assertThatThrownBy(()->AdminEntryMatcher.match(json.readTree("{\"total_counts\":2,\"data\":["+row+"]}"),input,null)).hasMessageContaining("incomplete");
    }
    @Test void identityMatchStillVerifiesWorkflowAndAvailableApplicantDetails()throws Exception {
        var body=json.readTree("{\"total_counts\":1,\"data\":["+row+"]}");
        assertThat(AdminEntryMatcher.match(body,input,"other-entry")).isNull();
        assertThat(AdminEntryMatcher.match(json.readTree("{\"data\":["+row.replace("pennydrop_only","other_workflow")+"]}"),input,"fixture-entry")).isNull();
        assertThatThrownBy(()->AdminEntryMatcher.match(json.readTree("{\"data\":["+row.replace("QA Fixture","Other Fixture")+"]}"),input,"fixture-entry")).hasMessageContaining("details differ");
    }
    @Test void credentialsAreEncryptedAndNeverPrinted(@TempDir Path directory)throws Exception {
        AdminCredentials.save(directory,"fixture-login","fixture-password");AdminCredentials restored=AdminCredentials.readLocal(directory);
        assertThat(restored.username()).isEqualTo("fixture-login");assertThat(restored.password()).isEqualTo("fixture-password");assertThat(restored.toString()).doesNotContain("fixture-login","fixture-password");
        assertThat(Files.readString(directory.resolve("credentials.enc"),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("fixture-login","fixture-password");
    }
    @Test void cancellationInterruptsTheCleanupGapWithoutAnyAdminAction()throws Exception {
        RunRecord run=new RunRecord();run.requestCancel();long start=System.nanoTime();
        assertThatThrownBy(()->AdminCleanupFlow.pause(java.time.Duration.ofSeconds(10),run)).isInstanceOf(java.util.concurrent.CancellationException.class);
        assertThat(java.time.Duration.ofNanos(System.nanoTime()-start)).isLessThan(java.time.Duration.ofSeconds(1));
    }
    @Test void cleanupCannotStartBeforeThePennyDropScreenshotIsSaved() {
        RunRecord run=new RunRecord();run.workflowStage=WorkflowStage.WAITING_FOR_RESULT;run.otpSubmitted=true;
        var driver=org.mockito.Mockito.mock(org.openqa.selenium.WebDriver.class);
        assertThatThrownBy(()->AdminCleanupFlow.complete(driver,null,run,input,null,null,new AdminCredentials("fixture-login","fixture-password"),"http://127.0.0.1/",java.time.Duration.ZERO,java.time.Duration.ZERO)).hasMessageContaining("result capture");
        org.mockito.Mockito.verifyNoInteractions(driver);
    }
}
