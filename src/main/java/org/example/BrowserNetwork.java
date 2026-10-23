package org.example;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.devtools.*;

/** Uses supported Selenium DevTools commands/events with pinned v151 domain support. */
public final class BrowserNetwork implements AutoCloseable {
    private final DevTools devTools;
    /** Internal correlation only. Callers must not persist bodies, applicant fields or credentials. */
    @SuppressWarnings("unchecked")
    com.fasterxml.jackson.databind.JsonNode responseJson(String requestId) {
        try {
            Map<String,Object> body=devTools.send(new Command<>("Network.getResponseBody",Map.of("requestId",requestId),input->(Map<String,Object>)input.read(Map.class)));
            String text=String.valueOf(body.getOrDefault("body",""));
            if(Boolean.TRUE.equals(body.get("base64Encoded")))text=new String(Base64.getDecoder().decode(text),StandardCharsets.UTF_8);
            return new ObjectMapper().readTree(text);
        }catch(Exception unavailable){return null;}
    }
    /** Read only the response already produced by Chrome; never issue another HTTP request. */
    @SuppressWarnings("unchecked")
    public Map<String,Object> applicationFeedback(String requestId) {
        Map<String,Object> result=new LinkedHashMap<>();
        try {
            Map<String,Object> body=devTools.send(new Command<>("Network.getResponseBody",Map.of("requestId",requestId),input->(Map<String,Object>)input.read(Map.class)));
            String text=String.valueOf(body.getOrDefault("body",""));
            if(Boolean.TRUE.equals(body.get("base64Encoded"))) text=new String(Base64.getDecoder().decode(text),StandardCharsets.UTF_8);
            var json=new ObjectMapper().readTree(text);
            if(json!=null && json.isObject()) {
                for(String key:new String[]{"success","redirect","refresh","show_popup"})
                    if(json.path(key).isBoolean()) result.put(key,json.path(key).booleanValue());
                for(String key:new String[]{"msg","message"})
                    if(json.path(key).isTextual()) result.put(key,JourneyPage.safeText(json.path(key).textValue()));
            }
        } catch(Exception unavailable) { result.put("note","Chrome response body unavailable or not JSON; HTTP status remains in network report"); }
        return result;
    }
    @SuppressWarnings("unchecked")
    public BrowserNetwork(ChromeDriver driver, NetworkLedger ledger) {
        devTools=driver.getDevTools(); devTools.createSession();
        for(String event: new String[]{"requestWillBeSent","responseReceived","responseReceivedExtraInfo",
                "requestServedFromCache","loadingFinished","loadingFailed"}) {
            Event<Map<String,Object>> e=new Event<>("Network."+event, input->(Map<String,Object>)input.read(Map.class));
            devTools.addListener(e, data->ledger.accept(event,data));
        }
        devTools.send(new Command<>("Network.enable",Map.of()));
    }
    public void close() { devTools.clearListeners(); devTools.disconnectSession(); }
}
