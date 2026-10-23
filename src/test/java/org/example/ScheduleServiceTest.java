package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduleServiceTest {
    @TempDir Path directory;
    final RunService runs=mock(RunService.class);
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final MutableClock clock=new MutableClock();
    final RunInput input=new RunInput("6999999999","Fixture","QA Fixture","TEST0000000","0000123","0000123","Fixture",RunInput.OtpMode.FIXED,"0007",RunInput.BrowserMode.HEADLESS);
    ScheduleService service(){return new ScheduleService(runs,new ScheduleStore(directory,mapper),clock,Validation.buildDefaultValidatorFactory().getValidator(),true);}
    String create(ScheduleService s,String time){return (String)s.create(new ScheduleRequest(input,time)).get("id");}
    @Test void scheduledRecipientsSurviveRestartAndAreUsedForTheScheduledRun(){
        RunInput notified=new RunInput(input.mobile(),input.dependency(),input.name(),input.ifsc(),input.accountNumber(),input.confirmAccountNumber(),input.accountType(),input.otpMode(),input.fixedOtp(),input.browserMode(),java.util.List.of("qa@example.test"));
        ScheduleService s=service();String id=(String)s.create(new ScheduleRequest(notified,"2026-10-14T05:31")).get("id");
        ScheduleService restarted=service();assertThat(((RunInput)restarted.inputs(id).get("input")).notificationEmails()).containsExactly("qa@example.test");
        RunRecord run=new RunRecord();when(runs.startScheduled(notified,id)).thenReturn(run);clock.now=Instant.parse("2026-10-14T00:01:00Z");restarted.tick();verify(runs,times(1)).startScheduled(notified,id);
        run.status="SUCCEEDED";run.notificationPending=true;restarted.tick();assertThat(restarted.list().get(0).get("status")).isEqualTo(SavedSchedule.Status.RUNNING);
        run.notificationPending=false;run.finishedAt=clock.instant().toString();restarted.tick();assertThat(service().report(id).get("notificationPending")).isEqualTo(false);
    }
    @Test void storesSnapshotEncryptedAndUsesISTAcrossRestart() throws Exception {
        ScheduleService s=service();String id=create(s,"2026-10-15T10:30");
        assertThat(s.list().get(0).get("scheduledAt")).isEqualTo("2026-10-15T05:00:00Z");
        assertThat(new String(Files.readAllBytes(directory.resolve("schedules.enc")),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("QA Fixture","0000123");
        ScheduleService restarted=service();assertThat(restarted.inputs(id).get("input")).isEqualTo(input);
        restarted.tick();verifyNoInteractions(runs);
    }
    @Test void launchesOnlyWhenDueAndExactlyOnceThenPersistsReport() {
        ScheduleService s=service();String id=create(s,"2026-10-14T05:31");RunRecord run=new RunRecord();
        when(runs.startScheduled(input,id)).thenReturn(run);s.tick();verifyNoInteractions(runs);
        clock.now=Instant.parse("2026-10-14T00:01:00Z");s.tick();s.tick();verify(runs,times(1)).startScheduled(input,id);
        run.status="SUCCEEDED";run.finishedAt=clock.instant().toString();s.tick();
        assertThat(service().report(id).get("status")).isEqualTo("SUCCEEDED");
    }
    @Test void busyRunnerQueuesWithoutLaunchingAndCanCancel() {
        ScheduleService s=service();String id=create(s,"2026-10-14T05:31");
        when(runs.startScheduled(input,id)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        clock.now=Instant.parse("2026-10-14T00:01:00Z");s.tick();
        assertThat(s.list().get(0).get("status")).isEqualTo(SavedSchedule.Status.WAITING_FOR_RUNNER);
        s.cancel(id);s.tick();verify(runs,times(1)).startScheduled(input,id);
        assertThat(service().list().get(0).get("status")).isEqualTo(SavedSchedule.Status.CANCELLED);
    }
    @Test void busyScheduleUsesSavedValuesWhenRunnerIsFree() {
        ScheduleService s=service();String id=create(s,"2026-10-14T05:31");RunRecord run=new RunRecord();
        when(runs.startScheduled(input,id)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT)).thenReturn(run);
        clock.now=Instant.parse("2026-10-14T00:01:00Z");s.tick();clock.now=clock.now.plusSeconds(120);s.tick();
        assertThat(s.list().get(0).get("status")).isEqualTo(SavedSchedule.Status.RUNNING);verify(runs,times(2)).startScheduled(input,id);
    }
    @Test void restartMarksMissedAndInterruptedInsteadOfRetrying() {
        ScheduleService s=service();String missed=create(s,"2026-10-14T05:31");String active=create(s,"2026-10-14T05:32");
        clock.now=Instant.parse("2026-10-14T00:01:00Z");assertThat(service().list().stream().filter(x->x.get("id").equals(missed)).findFirst().orElseThrow().get("status")).isEqualTo(SavedSchedule.Status.MISSED);
        ScheduleService restarted=service();clock.now=Instant.parse("2026-10-14T00:02:00Z");when(runs.startScheduled(input,active)).thenReturn(new RunRecord());restarted.tick();
        reset(runs);ScheduleService next=service();next.tick();verifyNoInteractions(runs);
        assertThat(next.list().stream().filter(x->x.get("id").equals(active)).findFirst().orElseThrow().get("status")).isEqualTo(SavedSchedule.Status.INTERRUPTED);
    }
    @Test void rejectsPastInvalidAndMismatchedInputsAndDeletesSavedValues() {
        ScheduleService s=service();assertThatThrownBy(()->create(s,"2026-10-14T05:30")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->create(s,"not a date")).isInstanceOf(ResponseStatusException.class);
        RunInput bad=new RunInput(input.mobile(),input.dependency(),input.name(),input.ifsc(),"0000123","321",input.accountType(),input.otpMode(),"007",input.browserMode());
        assertThatThrownBy(()->s.create(new ScheduleRequest(bad,"2026-10-15T10:30"))).isInstanceOf(ResponseStatusException.class);
        String id=create(s,"2026-10-15T10:30");s.delete(id);assertThat(service().list()).isEmpty();verifyNoInteractions(runs);
    }
    static final class MutableClock extends Clock {Instant now=Instant.parse("2026-10-14T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}}
}
