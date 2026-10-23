package org.example;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Thread-safe reducer for real CDP lifecycle events. One row per request ID + redirect hop. */
public final class NetworkLedger {
    public record Entry(String runId, String requestId, int hop, String method, String url, String resourceType,
                        String step, String startedAt, Integer status, Integer wireStatus, String state,
                        boolean cached, boolean serviceWorker, String error, String finishedAt) {}
    private static final class Row {
        String id, method="", url="", type="", step, start=Instant.now().toString(), end, state="PENDING", error;
        int hop; Integer status, wireStatus; boolean cached, serviceWorker, expectsExtra;
    }
    private final String runId;
    private final Supplier<String> step;
    private final Map<String,List<Row>> chains = new LinkedHashMap<>();
    private final Map<String,Deque<Integer>> extra = new HashMap<>();
    private boolean frozen;
    private long lastActivity = System.nanoTime();
    public NetworkLedger(String runId, Supplier<String> step) { this.runId=runId; this.step=step; }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object x) { return x instanceof Map<?,?> ? (Map<String,Object>)x : Map.of(); }
    private static String str(Object x) { return x==null ? "" : x.toString(); }
    private static Integer number(Object x) { return x instanceof Number n ? n.intValue() : null; }
    private static boolean api(String type) { return type.equals("Fetch") || type.equals("XHR"); }
    private static String endpoint(Object raw) {
        try { URI u=URI.create(str(raw)); return new URI(u.getScheme(),null,u.getHost(),u.getPort(),u.getPath(),null,null).toString(); }
        catch(Exception e) { return "[unparseable URL]"; }
    }
    private Row latest(String id) { List<Row> rows=chains.get(id); return rows==null || rows.isEmpty() ? null : rows.get(rows.size()-1); }
    private Row create(String id) {
        List<Row> rows=chains.computeIfAbsent(id,k->new ArrayList<>());
        Row r=new Row(); r.id=id; r.hop=rows.size(); r.step=step.get(); rows.add(r); return r;
    }
    private void response(Row r, Map<String,Object> response, boolean expectsExtra) {
        r.url=endpoint(response.getOrDefault("url",r.url)); r.status=number(response.get("status"));
        r.cached |= Boolean.TRUE.equals(response.get("fromDiskCache")) || Boolean.TRUE.equals(response.get("fromPrefetchCache"));
        r.serviceWorker |= Boolean.TRUE.equals(response.get("fromServiceWorker"));
        r.expectsExtra=expectsExtra;
        if (r.state.equals("PENDING")) r.state="RESPONSE_RECEIVED";
        matchExtra(r.id);
    }
    private void matchExtra(String id) {
        Deque<Integer> values=extra.get(id); if(values==null) return;
        for(Row r:chains.getOrDefault(id,List.of())) {
            if(r.expectsExtra && r.wireStatus==null && !values.isEmpty()) r.wireStatus=values.removeFirst();
        }
    }
    public synchronized void accept(String event, Map<String,Object> p) {
        if(frozen) return;
        String id=str(p.get("requestId")), type=str(p.get("type"));
        Row r=latest(id);
        switch(event) {
            case "requestWillBeSent" -> {
                if(!api(type) && r==null) return;
                Map<String,Object> redirect=map(p.get("redirectResponse"));
                if(!redirect.isEmpty()) {
                    if(r==null) r=create(id);
                    response(r,redirect,Boolean.TRUE.equals(p.get("redirectHasExtraInfo")));
                    r.state="REDIRECTED"; r.end=Instant.now().toString();
                }
                r=create(id); Map<String,Object> request=map(p.get("request"));
                r.method=str(request.get("method")); r.url=endpoint(request.get("url")); r.type=type;
            }
            case "responseReceived" -> {
                if(r==null && !api(type)) return;
                if(r==null) r=create(id);
                r.type=type; response(r,map(p.get("response")),Boolean.TRUE.equals(p.get("hasExtraInfo")));
            }
            case "responseReceivedExtraInfo" -> {
                // Extra info can precede the request/response callback, so retain until correlated.
                Integer code=number(p.get("statusCode"));
                if(code!=null) extra.computeIfAbsent(id,k->new ArrayDeque<>()).addLast(code);
                matchExtra(id);
                if(r!=null) lastActivity=System.nanoTime();
                return;
            }
            case "requestServedFromCache" -> { if(r==null) return; r.cached=true; }
            case "loadingFinished" -> { if(r==null) return; r.state="COMPLETED"; r.end=Instant.now().toString(); }
            case "loadingFailed" -> {
                if(r==null && !api(type)) return;
                if(r==null) { r=create(id); r.type=type; }
                r.state="FAILED"; r.end=Instant.now().toString();
                r.error=str(p.get("errorText"));
                if(p.get("blockedReason")!=null) r.error += "; blocked="+p.get("blockedReason");
                if(p.get("corsErrorStatus")!=null) r.error += "; CORS="+p.get("corsErrorStatus");
            }
            default -> { return; }
        }
        lastActivity=System.nanoTime();
    }
    public synchronized boolean settled(long quietMillis) {
        return chains.values().stream().flatMap(List::stream).noneMatch(r->r.state.equals("PENDING") || r.state.equals("RESPONSE_RECEIVED"))
                && (System.nanoTime()-lastActivity)/1_000_000 >= quietMillis;
    }
    public synchronized List<Entry> snapshot() {
        return chains.values().stream().flatMap(List::stream).map(r->new Entry(runId,r.id,r.hop,r.method,r.url,r.type,r.step,r.start,
                r.status,r.wireStatus,r.state,r.cached,r.serviceWorker,r.error,r.end)).toList();
    }
    public synchronized void freeze() {
        frozen=true;
        chains.values().stream().flatMap(List::stream).filter(r->r.state.equals("PENDING") || r.state.equals("RESPONSE_RECEIVED"))
                .forEach(r->r.state="UNFINISHED");
        extra.clear();
    }
}
