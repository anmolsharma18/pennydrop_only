package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Validator;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleService {
    public static final ZoneId IST=ZoneId.of("Asia/Kolkata");
    private final RunService runs;
    private final ScheduleStore store;
    private final Clock clock;
    private final Validator validator;
    private final boolean enabled;
    private final LinkedHashMap<String,SavedSchedule> records=new LinkedHashMap<>();
    private final Map<String,RunRecord> running=new HashMap<>();
    private String schedulerError;
    @org.springframework.beans.factory.annotation.Autowired
    public ScheduleService(RunService runs,ObjectMapper mapper,Validator validator,
                           @Value("${portal.scheduler-directory:data/scheduler}") String directory,
                           @Value("${portal.scheduler-enabled:true}") boolean enabled) {
        this(runs,new ScheduleStore(Path.of(directory),mapper),Clock.systemUTC(),validator,enabled);
    }
    ScheduleService(RunService runs,ScheduleStore store,Clock clock,Validator validator,boolean enabled) {
        this.runs=runs;this.store=store;this.clock=clock;this.validator=validator;this.enabled=enabled;
        boolean changed=false;
        for(SavedSchedule schedule:store.load()) {
            records.put(schedule.id,schedule);
            if(schedule.status==SavedSchedule.Status.RUNNING||schedule.status==SavedSchedule.Status.DISPATCHING) {
                schedule.status=SavedSchedule.Status.INTERRUPTED;schedule.message="Portal stopped during dispatch/run. Not automatically retried; review before creating another schedule.";changed=true;
            }else if(pending(schedule)&&!schedule.scheduledAt.isAfter(clock.instant())) {
                schedule.status=SavedSchedule.Status.MISSED;schedule.message="Scheduled time passed while the portal was stopped. Create a new future schedule to run.";changed=true;
            }
        }
        if(changed)store.save(records.values());
    }
    public synchronized Map<String,Object> create(ScheduleRequest request) {
        if(!validator.validate(request).isEmpty())throw bad("Required journey inputs and a schedule date/time must be valid");
        final LocalDateTime local;
        try {local=LocalDateTime.parse(request.scheduledLocalTime());}catch(DateTimeParseException e){throw bad("Enter a valid IST date and time, for example YYYY-MM-DDTHH:mm");}
        Instant when=local.atZone(IST).toInstant();if(!when.isAfter(clock.instant()))throw bad("Scheduled date and time must be in the future in IST");
        SavedSchedule schedule=new SavedSchedule();schedule.id=UUID.randomUUID().toString();schedule.createdAt=clock.instant().toString();
        schedule.scheduledLocalTime=local.toString();schedule.scheduledAt=when;schedule.input=request.input();schedule.status=SavedSchedule.Status.PENDING;
        records.put(schedule.id,schedule);
        try {store.save(records.values());}catch(RuntimeException e){records.remove(schedule.id);throw e;}
        return schedule.view();
    }
    public synchronized List<Map<String,Object>> list(){return records.values().stream().sorted(Comparator.comparing(s->s.scheduledAt)).map(SavedSchedule::view).toList();}
    public synchronized Map<String,Object> inputs(String id){SavedSchedule s=get(id);return Map.of("input",s.input,"scheduledLocalTime",s.scheduledLocalTime,"timeZone",IST.getId());}
    public synchronized Map<String,Object> cancel(String id){SavedSchedule s=get(id);if(!pending(s))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only pending schedules can be cancelled; cancel an active run in Run monitor");s.status=SavedSchedule.Status.CANCELLED;s.message="Cancelled before launch";store.save(records.values());return s.view();}
    public synchronized void delete(String id){SavedSchedule s=get(id);if(s.status==SavedSchedule.Status.RUNNING||s.status==SavedSchedule.Status.DISPATCHING)throw new ResponseStatusException(HttpStatus.CONFLICT,"Cancel the active run before deleting its schedule");records.remove(id);try{store.save(records.values());}catch(RuntimeException e){records.put(id,s);throw e;}}
    public synchronized Map<String,Object> report(String id){SavedSchedule s=get(id);RunRecord live=running.get(id);if(live!=null)return live.view();if(s.report!=null)return s.report;if(s.runId!=null){try{return runs.get(s.runId).view();}catch(ResponseStatusException ignored){}}throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No run report is available for this schedule yet");}
    public synchronized boolean ownsRun(String id){return records.values().stream().anyMatch(s->id.equals(s.runId));}
    public synchronized Map<String,Object> health(){return Map.of("enabled",enabled,"timeZone",IST.getId(),"currentTime",clock.instant().toString(),"error",schedulerError==null?"":schedulerError);}
    @Scheduled(fixedDelayString="${portal.scheduler-poll-ms:1000}")
    public void dispatch(){if(!enabled)return;try{tick();}catch(Exception e){synchronized(this){schedulerError="Schedule dispatch/storage failed. Check storage permissions; ambiguous launches will not be retried.";}}}
    synchronized void tick() {
        schedulerError=null;
        for(var entry:new ArrayList<>(running.entrySet())) {
            RunRecord run=entry.getValue();
            if(List.of("SUCCEEDED","FAILED","CANCELLED").contains(run.status)&&!run.notificationPending) {
                SavedSchedule s=records.get(entry.getKey());s.status=SavedSchedule.Status.valueOf(run.status);s.finishedAt=run.finishedAt;s.message=run.error;s.report=run.view();
                store.save(records.values());running.remove(entry.getKey());
            }
        }
        Instant now=clock.instant();
        for(SavedSchedule s:records.values().stream().filter(ScheduleService::pending).filter(s->!s.scheduledAt.isAfter(now)).sorted(Comparator.comparing(s->s.scheduledAt)).toList()) {
            if(s.status==SavedSchedule.Status.PENDING&&Duration.between(s.scheduledAt,now).getSeconds()>60){s.status=SavedSchedule.Status.MISSED;s.message="Portal/computer was unavailable at the scheduled time. Create a new future schedule.";store.save(records.values());continue;}
            // Persist a claim BEFORE launch. Restarting a claimed/running job never sends the journey again.
            s.status=SavedSchedule.Status.DISPATCHING;store.save(records.values());
            try {
                RunRecord run=runs.startScheduled(s.input,s.id);s.runId=run.id;s.startedAt=run.startedAt;s.status=SavedSchedule.Status.RUNNING;s.message=null;running.put(s.id,run);store.save(records.values());
            }catch(ResponseStatusException busy) {
                if(busy.getStatusCode().value()!=409)throw busy;
                s.status=SavedSchedule.Status.WAITING_FOR_RUNNER;s.message="Due; waiting for the current journey to finish";store.save(records.values());
            }
        }
    }
    private static boolean pending(SavedSchedule s){return s.status==SavedSchedule.Status.PENDING||s.status==SavedSchedule.Status.WAITING_FOR_RUNNER;}
    private SavedSchedule get(String id){SavedSchedule s=records.get(id);if(s==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Schedule not found");return s;}
    private static ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
    @PreDestroy public synchronized void stop(){for(var entry:running.entrySet()){SavedSchedule s=records.get(entry.getKey());s.status=SavedSchedule.Status.INTERRUPTED;s.message="Portal stopped during the run; not automatically retried";}if(!running.isEmpty())store.save(records.values());}
}
