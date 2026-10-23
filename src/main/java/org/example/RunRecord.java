package org.example;

import java.time.Instant;
import java.util.*;

public final class RunRecord {
    public enum Kind { JOURNEY, DELETE_ONLY }
    public final Kind kind;
    public RunRecord(){this(Kind.JOURNEY);}
    public RunRecord(Kind kind){this.kind=Objects.requireNonNull(kind);if(kind==Kind.DELETE_ONLY)workflowStage=WorkflowStage.ADMIN_LOGIN;}
    public final String id=UUID.randomUUID().toString();
    public final String startedAt=Instant.now().toString();
    public volatile String status="QUEUED", step="Queued", finishedAt, error;
    public volatile WorkflowStage workflowStage=WorkflowStage.APPLICANT_DETAILS;
    public volatile boolean otpEntered, otpSubmitted;
    public volatile boolean notificationPending;
    private volatile boolean cancelRequested;
    public final NetworkLedger network=new NetworkLedger(id,()->step);
    private final List<Map<String,String>> logs=new ArrayList<>();
    private final Map<String,Object> diagnostics=new LinkedHashMap<>();
    private final List<Map<String,String>> screenshots=new ArrayList<>();
    public synchronized void screenshot(String label,String url) {
        screenshots.removeIf(item->item.get("label").equals(label));
        var item=Map.of("label",label,"url",url);screenshots.add(item);diagnostics.put("screenshot",item);
    }
    public synchronized List<Map<String,String>> screenshots(){return List.copyOf(screenshots);}
    public synchronized void diagnostic(String key,Object value) { diagnostics.put(key,value); }
    public synchronized void transition(WorkflowStage next,String message) {
        checkCancelled();
        boolean withoutCleanup=workflowStage==WorkflowStage.WAITING_FOR_RESULT&&next==WorkflowStage.COMPLETED;
        if(next!=workflowStage && next.ordinal()!=workflowStage.ordinal()+1&&!withoutCleanup)
            throw new IllegalStateException("Invalid workflow transition "+workflowStage+" -> "+next);
        if(next==WorkflowStage.WAITING_FOR_BANK && !otpSubmitted)
            throw new IllegalStateException("Cannot wait for bank before this run submits OTP");
        workflowStage=next; stage(message);
    }
    public void checkCancelled() {
        if(cancelRequested || Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Run cancelled");
    }
    public synchronized void requestCancel() { cancelRequested=true; stage("Cancellation requested; browser cleanup will follow"); }
    public boolean isCancellationRequested() { return cancelRequested; }
    public synchronized void markOtpEntered() {
        if(workflowStage!=WorkflowStage.ENTERING_OTP) throw new IllegalStateException("OTP entry outside ENTERING_OTP");
        otpEntered=true; stage("OTP entered and verified for this run (value omitted)");
    }
    public synchronized void markOtpSubmitted() {
        if(workflowStage!=WorkflowStage.ENTERING_OTP || !otpEntered) throw new IllegalStateException("OTP Submit without verified entry");
        otpSubmitted=true; stage("OTP Submit clicked once; OTP-submitted state set");
    }
    public synchronized void stage(String text) {
        step=text; logs.add(Map.of("time",Instant.now().toString(),"message",text,"workflowStage",workflowStage.name()));
    }
    public synchronized Map<String,Object> view() {
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("id",id); data.put("kind",kind); data.put("status",status); data.put("step",step); data.put("startedAt",startedAt);
        data.put("finishedAt",finishedAt); data.put("error",error); data.put("logs",List.copyOf(logs)); data.put("network",network.snapshot());
        data.put("notificationPending",notificationPending);
        data.put("diagnostics",Map.copyOf(diagnostics));
        data.put("workflowStage",workflowStage); data.put("otpEntered",otpEntered); data.put("otpSubmitted",otpSubmitted);
        data.put("cancelRequested",cancelRequested);
        data.put("screenshots",List.copyOf(screenshots));
        return data;
    }
}
