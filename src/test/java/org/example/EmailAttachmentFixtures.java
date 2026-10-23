package org.example;

import java.nio.file.*;
import java.util.*;

final class EmailAttachmentFixtures {
    static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lN8AAAAASUVORK5CYII=");
    static void screenshot(RunRecord run,String label)throws Exception{Path file=RunScreenshots.path(run.id,label);Files.createDirectories(file.getParent());Files.write(file,PNG);run.screenshot(label,"/api/runs/"+run.id+"/screenshots/"+label);}
    static void request(RunRecord run,String id){run.network.accept("requestWillBeSent",Map.of("requestId",id,"type","Fetch","request",Map.of("method","POST","url","https://fixture.invalid/api/bank?token=private-query")));}
    static void complete(RunRecord run,String id,int status){run.network.accept("responseReceived",Map.of("requestId",id,"type","Fetch","response",Map.of("url","https://fixture.invalid/api/bank?token=private-query","status",status)));run.network.accept("loadingFinished",Map.of("requestId",id));}
}
