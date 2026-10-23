package org.example;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.internet.MimeMessage;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunNotificationTest {
    @Test void scheduledWorkerKeepsBusyUntilItsOwnRecipientSnapshotIsProcessed()throws Exception{
        KycJourney journey=mock(KycJourney.class);AdminDeleteJourney deletion=mock(AdminDeleteJourney.class);
        doAnswer(call->{RunRecord run=call.getArgument(1);EmailAttachmentFixtures.screenshot(run,"result-page");run.status="SUCCEEDED";run.workflowStage=WorkflowStage.COMPLETED;run.finishedAt="2026-10-16T00:00:00Z";return null;}).when(journey).execute(any(),any());
        CountDownLatch sending=new CountDownLatch(1),release=new CountDownLatch(1);JavaMailSender sender=mock(JavaMailSender.class);when(sender.createMimeMessage()).thenAnswer(call->new MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));
        doAnswer(call->{sending.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return null;}).when(sender).send(any(MimeMessage.class));
        RunService service=new RunService(journey,deletion,new SuccessNotifier(sender,"sender@example.test",true));
        RunInput input=new RunInput("6999999999","Fixture","QA Fixture","TEST0000000","0000123","0000123","Fixture",RunInput.OtpMode.FIXED,"0007",RunInput.BrowserMode.HEADLESS,List.of("saved@example.test"));
        try{
            RunRecord run=service.startScheduled(input,"fixture-schedule");assertThat(sending.await(5,TimeUnit.SECONDS)).isTrue();assertThat(run.notificationPending).isTrue();
            assertThatThrownBy(()->service.start(input)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            release.countDown();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(run.notificationPending&&System.nanoTime()<deadline)Thread.sleep(10);
            assertThat(run.notificationPending).isFalse();assertThat(run.status).isEqualTo("SUCCEEDED");
            verify(sender,times(1)).send(argThat((MimeMessage message)->{try{return message.getAllRecipients().length==1&&message.getAllRecipients()[0].toString().equals("saved@example.test");}catch(Exception e){return false;}}));
            verify(journey,times(1)).execute(eq(input),eq(run));verifyNoInteractions(deletion);
        }finally{release.countDown();service.stop();}
    }
}

