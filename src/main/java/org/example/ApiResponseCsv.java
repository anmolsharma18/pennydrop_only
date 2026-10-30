package org.example;

import java.nio.charset.StandardCharsets;

/** CSV rows describe observed responses; an empty status means no response was received. */
final class ApiResponseCsv {
    static final String MIME="text/csv; charset=UTF-8";
    static byte[] create(RunRecord run) {
        StringBuilder csv=new StringBuilder("\uFEFF");
        row(csv,"Run ID","Run type","Run status","Request ID","Redirect hop","Method","Endpoint","HTTP status","Wire status","Lifecycle","Workflow step","Resource type","Cached","Service worker","Started at UTC","Finished at UTC","Error");
        for(NetworkLedger.Entry e:run.network.snapshot()) {
            row(csv,run.id,run.kind,run.status,e.requestId(),e.hop(),e.method(),e.url(),e.status(),e.wireStatus(),e.state(),e.step(),e.resourceType(),e.cached(),e.serviceWorker(),e.startedAt(),e.finishedAt(),e.error());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void row(StringBuilder csv,Object... values) {
        for(int i=0;i<values.length;i++) {
            if(i>0)csv.append(',');
            String value=values[i]==null?"":values[i].toString();
            // Keep captured text literal when the CSV is opened in spreadsheet applications.
            String trimmed=value.stripLeading();
            if(!trimmed.isEmpty()&&"=+@-".indexOf(trimmed.charAt(0))>=0||value.startsWith("\t")||value.startsWith("\r")||value.startsWith("\n"))value="'"+value;
            csv.append('"').append(value.replace("\"","\"\"")).append('"');
        }
        csv.append("\r\n");
    }
}
