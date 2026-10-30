package org.example;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RunService {
    private final KycJourney journey;
    private final AdminDeleteJourney deletion;
    private final SuccessNotifier notifications;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String,RunRecord> history=new LinkedHashMap<>();
    private boolean active;
    public RunService(KycJourney journey,AdminDeleteJourney deletion,SuccessNotifier notifications) { this.journey=journey;this.deletion=deletion;this.notifications=notifications; }
    public synchronized RunRecord start(RunInput input) {
        return startScheduled(input,null);
    }
    public synchronized RunRecord startScheduled(RunInput input,String scheduleId) {
        return submit(RunRecord.Kind.JOURNEY,scheduleId,DeleteInput.from(input),run->journey.execute(input,run));
    }
    public synchronized RunRecord startDelete(DeleteInput input) {
        return submit(RunRecord.Kind.DELETE_ONLY,null,input,run->deletion.execute(input,run));
    }
    private RunRecord submit(RunRecord.Kind kind,String scheduleId,DeleteInput input,java.util.function.Consumer<RunRecord> action) {
        if(active) throw new ResponseStatusException(HttpStatus.CONFLICT,"A run is already active");
        while(history.size()>=20) history.remove(history.keySet().iterator().next());
        RunRecord run=new RunRecord(kind); history.put(run.id,run); active=true;
        run.notificationPending=!input.notificationEmails().isEmpty();
        if(scheduleId!=null)run.diagnostic("scheduleId",scheduleId);
        worker.submit(()->{ try {
                try{action.accept(run);}catch(RuntimeException e){
                    run.status=e instanceof CancellationException?"CANCELLED":"FAILED";
                    run.finishedAt=java.time.Instant.now().toString();
                    run.error="Automation stopped unexpectedly ("+e.getClass().getSimpleName()+"); review the run diagnostics";
                    run.network.freeze();
                }
                try{notifications.afterRun(run,input);}catch(RuntimeException e){run.diagnostic("emailNotification",Map.of("status","FAILED","message","Notification processing failed; automation was not retried"));}
            }
            finally { run.notificationPending=false;synchronized(this) { active=false; } } });
        return run;
    }
    public synchronized RunRecord get(String id) {
        RunRecord run=history.get(id);
        if(run==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Run not found (history is in memory)");
        return run;
    }
    public synchronized List<Map<String,Object>> list() { return history.values().stream().map(RunRecord::view).toList(); }
    public synchronized RunRecord cancel(String id) {
        RunRecord run=get(id);
        if(List.of("SUCCEEDED","FAILED","CANCELLED").contains(run.status)||run.workflowStage==WorkflowStage.COMPLETED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Run is already complete");
        run.requestCancel();return run;
    }
    @PreDestroy public synchronized void stop() {
        history.values().stream().filter(r->!List.of("SUCCEEDED","FAILED","CANCELLED").contains(r.status)).forEach(RunRecord::requestCancel);
        worker.shutdownNow();
    }
}
