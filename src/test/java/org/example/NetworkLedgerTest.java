package org.example;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class NetworkLedgerTest {
    private final NetworkLedger ledger=new NetworkLedger("run-1",()->"IFSC");
    private void request(String id,String url) { ledger.accept("requestWillBeSent",Map.of("requestId",id,"type","XHR","request",Map.of("url",url,"method","POST"))); }
    private void response(String id,int status,boolean extra) { ledger.accept("responseReceived",Map.of("requestId",id,"type","XHR","response",Map.of("url","https://qa.invalid/api","status",status),"hasExtraInfo",extra)); }
    @Test void concurrentRepeatedUrlsFailureAndUnfinishedStaySeparate() {
        request("a","https://qa.invalid/api?account=secret"); request("b","https://qa.invalid/api"); request("c","https://qa.invalid/api");
        response("b",201,false); ledger.accept("loadingFinished",Map.of("requestId","b"));
        ledger.accept("loadingFailed",Map.of("requestId","a","type","XHR","errorText","net::ERR_FAILED","corsErrorStatus",Map.of("corsError","MissingAllowOriginHeader")));
        ledger.freeze(); var rows=ledger.snapshot();
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).status()).isNull(); assertThat(rows.get(0).state()).isEqualTo("FAILED");
        assertThat(rows.get(0).url()).doesNotContain("secret"); assertThat(rows.get(0).error()).contains("CORS");
        assertThat(rows.get(1).status()).isEqualTo(201); assertThat(rows.get(1).state()).isEqualTo("COMPLETED");
        assertThat(rows.get(2).state()).isEqualTo("UNFINISHED");
    }
    @Test void redirectHopsAndEarlyExtraInfoAreCorrelated() {
        ledger.accept("responseReceivedExtraInfo",Map.of("requestId","a","statusCode",302));
        request("a","https://qa.invalid/first");
        ledger.accept("requestWillBeSent",Map.of("requestId","a","type","Fetch","request",Map.of("url","https://other.invalid/second","method","GET"),"redirectResponse",Map.of("url","https://qa.invalid/first","status",302),"redirectHasExtraInfo",true));
        ledger.accept("responseReceivedExtraInfo",Map.of("requestId","a","statusCode",304));
        response("a",200,true); ledger.accept("requestServedFromCache",Map.of("requestId","a"));
        ledger.accept("loadingFinished",Map.of("requestId","a"));
        var rows=ledger.snapshot(); assertThat(rows).hasSize(2);
        assertThat(rows.get(0).status()).isEqualTo(302); assertThat(rows.get(0).wireStatus()).isEqualTo(302);
        assertThat(rows.get(0).state()).isEqualTo("REDIRECTED"); assertThat(rows.get(1).hop()).isEqualTo(1);
        assertThat(rows.get(1).status()).isEqualTo(200); assertThat(rows.get(1).wireStatus()).isEqualTo(304);
        assertThat(rows.get(1).cached()).isTrue(); assertThat(rows.get(1).runId()).isEqualTo("run-1");
    }
    @Test void responseThenTransportFailureRetainsObservedStatusAndFreezeRejectsLateEvents() {
        request("a","https://qa.invalid/api"); response("a",200,false);
        ledger.accept("loadingFailed",Map.of("requestId","a","errorText","net::ERR_CONNECTION_RESET"));
        assertThat(ledger.snapshot().get(0).status()).isEqualTo(200);
        request("pending","https://qa.invalid/api"); response("pending",202,false); ledger.freeze();
        ledger.accept("loadingFinished",Map.of("requestId","pending"));
        assertThat(ledger.snapshot().get(1).state()).isEqualTo("UNFINISHED");
    }
    @Test void documentResourcesAreExcluded() {
        ledger.accept("requestWillBeSent",Map.of("requestId","doc","type","Document","request",Map.of("url","https://qa.invalid","method","GET")));
        assertThat(ledger.snapshot()).isEmpty();
    }
}
