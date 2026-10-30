package org.example;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApiResponseCsvTest {
    @Test void includesEveryHopAndMissingResponseWithoutInventingStatusCodes(){
        RunRecord run=new RunRecord();run.status="FAILED";run.stage("Bank, \"save\"\r\nresponse");
        EmailAttachmentFixtures.request(run,"redirect");
        run.network.accept("requestWillBeSent",Map.of("requestId","redirect","type","Fetch","redirectResponse",Map.of("url","https://fixture.invalid/old?secret=hidden","status",302),"request",Map.of("method","GET","url","https://fixture.invalid/api/bank")));
        EmailAttachmentFixtures.complete(run,"redirect",200);
        EmailAttachmentFixtures.request(run,"repeated");EmailAttachmentFixtures.complete(run,"repeated",500);
        EmailAttachmentFixtures.request(run,"failed");run.network.accept("loadingFailed",Map.of("requestId","failed","errorText","net::ERR_FAILED"));
        EmailAttachmentFixtures.request(run,"unfinished");run.network.freeze();
        String csv=new String(ApiResponseCsv.create(run),StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF\"Run ID\"").contains("\"FAILED\"","\"redirect\",\"0\"","\"redirect\",\"1\"","\"302\"","\"200\"","\"500\"",
            "\"\",\"\",\"FAILED\"","\"\",\"\",\"UNFINISHED\"","net::ERR_FAILED","\"Bank, \"\"save\"\"\r\nresponse\"")
            .doesNotContain("?","secret=hidden","private-query");
    }
    @Test void emptyCaptureHasHeadersAndCapturedTextCannotBecomeSpreadsheetFormulas(){
        RunRecord run=new RunRecord();
        assertThat(new String(ApiResponseCsv.create(run),StandardCharsets.UTF_8).lines().count()).isEqualTo(1);
        run.stage("  =HYPERLINK(\"test\")\ntext");EmailAttachmentFixtures.request(run,"=1+1");run.network.freeze();
        String csv=new String(ApiResponseCsv.create(run),StandardCharsets.UTF_8);
        assertThat(csv).contains("\"'=1+1\"","\"'  =HYPERLINK");
    }
}
