package org.example;

import java.util.*;
import org.openqa.selenium.*;

/** Run-owned identity evidence, kept in memory rather than exposing applicant IDs in diagnostics. */
final class JourneyRecordIdentity {
    private final Set<String> identities=new LinkedHashSet<>();
    private final Set<String> inspected=new HashSet<>();
    void capture(WebDriver driver,BrowserNetwork monitor,RunRecord run) {
        for(WebElement e:driver.findElements(By.id("user_identity")))add(e.getDomProperty("value"));
        for(var response:run.network.snapshot()) {
            if(!response.state().equals("COMPLETED")||!inspected.add(response.requestId()))continue;
            String path=java.net.URI.create(response.url()).getPath();
            if(!path.startsWith("/democapital/pennydrop_only/")&&!path.equals("/api/send-otp"))continue;
            var json=monitor.responseJson(response.requestId());if(json==null||!json.isObject())continue;
            for(var object:List.of(json,json.path("data")))for(String key:List.of("identity","user_identity"))if(object.path(key).isTextual())add(object.path(key).asText());
        }
    }
    private void add(String id){if(id!=null&&!id.isBlank()&&!id.equalsIgnoreCase("none")&&!id.equalsIgnoreCase("null"))identities.add(id.trim());}
    String unique(){if(identities.size()>1)throw new IllegalStateException("Conflicting journey identity evidence; admin deletion stopped");return identities.stream().findFirst().orElse(null);}
}
