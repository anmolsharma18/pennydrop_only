package org.example;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuccessNotifierTest {
    private DeleteInput input(List<String> emails){return new DeleteInput("6999999999","QA Fixture","TEST0000000","0000123",RunInput.BrowserMode.HEADLESS,emails);}
    private RunRecord run(String status){RunRecord run=new RunRecord();run.status=status;run.finishedAt="2026-10-16T00:00:00Z";return run;}
    private Map<?,?> outcome(RunRecord run){return (Map<?,?>)((Map<?,?>)run.view().get("diagnostics")).get("emailNotification");}
    @Test void actualLocalSmtpAcceptsSeparateDeduplicatedNotifications()throws Exception{
        List<String> messages=new CopyOnWriteArrayList<>();
        try(ServerSocket server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"))){
            server.setSoTimeout(10000);
            ExecutorService executor=Executors.newSingleThreadExecutor();
            Future<?> fixture=executor.submit(()->{
                try{for(int i=0;i<2;i++)try(Socket socket=server.accept()){
                    socket.setSoTimeout(10000);var in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));var out=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true);
                    out.print("220 fixture ESMTP\r\n");out.flush();String line;
                    while((line=in.readLine())!=null){
                        if(line.startsWith("DATA")){out.print("354 End with dot\r\n");out.flush();StringBuilder body=new StringBuilder();while((line=in.readLine())!=null&&!line.equals("."))body.append(line).append('\n');messages.add(body.toString());out.print("250 Accepted\r\n");}
                        else if(line.startsWith("QUIT")){out.print("221 Bye\r\n");out.flush();break;}
                        else out.print("250 OK\r\n");out.flush();
                    }
                }}catch(Exception e){throw new RuntimeException(e);}
            });
            try{
                JavaMailSenderImpl mail=new JavaMailSenderImpl();mail.setHost("127.0.0.1");mail.setPort(server.getLocalPort());mail.getJavaMailProperties().setProperty("mail.smtp.timeout","5000");
                RunRecord run=run("SUCCEEDED");EmailAttachmentFixtures.screenshot(run,"result-page");EmailAttachmentFixtures.screenshot(run,"admin-delete-success");
                EmailAttachmentFixtures.request(run,"fixture-request");EmailAttachmentFixtures.complete(run,"fixture-request",200);run.network.freeze();
                new SuccessNotifier(mail,"sender@example.test",true).afterRun(run,input(List.of("one@example.test","ONE@example.test","two@example.test")));
                fixture.get(10,TimeUnit.SECONDS);assertThat(messages).hasSize(2);
                for(int i=0;i<2;i++){
                    MimeMessage received=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(messages.get(i).getBytes(StandardCharsets.UTF_8)));
                    assertThat(received.getAllRecipients()).hasSize(1);assertThat(received.getAllRecipients()[0].toString()).isEqualTo(i==0?"one@example.test":"two@example.test");
                    List<BodyPart> parts=new ArrayList<>();collect(received,parts);
                    List<BodyPart> attachments=parts.stream().filter(p->{try{return p.getFileName()!=null;}catch(Exception e){throw new RuntimeException(e);}}).toList();
                    assertThat(attachments).hasSize(3);
                    for(BodyPart part:attachments){
                        if(part.getFileName().endsWith(".xlsx")){assertThat(part.getContentType()).startsWith(ApiResponseWorkbook.MIME);try(XSSFWorkbook book=new XSSFWorkbook(part.getInputStream())){assertThat(book.getSheetAt(0).getRow(7).getCell(4).getNumericCellValue()).isEqualTo(200);}}
                        else assertThat(part.getInputStream().readAllBytes()).isEqualTo(EmailAttachmentFixtures.PNG);
                    }
                    var text=parts.stream().filter(p->{try{return p.isMimeType("text/plain");}catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow();
                    assertThat(text.getContent().toString()).contains("Automation completed successfully","****0123").doesNotContain("6999999999","0000123");
                }
                assertThat(outcome(run).get("status")).isEqualTo("ACCEPTED_BY_SMTP");assertThat(outcome(run).get("acceptedCount")).isEqualTo(2);
                assertThat(run.view().toString()).doesNotContain("one@example.test","two@example.test");
            }finally{executor.shutdownNow();}
        }
    }
    @Test void failuresAndCancellationNeverSendAndMailFailureNeverChangesAutomationSuccess()throws Exception{
        JavaMailSender sender=mock(JavaMailSender.class);SuccessNotifier notifier=new SuccessNotifier(sender,"sender@example.test",true);
        for(String state:List.of("FAILED","CANCELLED")){RunRecord run=run(state);notifier.afterRun(run,input(List.of("one@example.test")));assertThat(outcome(run).get("status")).isEqualTo("SKIPPED");}
        notifier.afterRun(run("SUCCEEDED"),input(List.of()));verifyNoInteractions(sender);
        RunRecord missing=run("SUCCEEDED");new SuccessNotifier(sender,"sender@example.test",false).afterRun(missing,input(List.of("one@example.test")));verifyNoInteractions(sender);assertThat(outcome(missing).get("status")).isEqualTo("NOT_CONFIGURED");
        when(sender.createMimeMessage()).thenAnswer(call->new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("private SMTP server feedback")).when(sender).send(any(MimeMessage.class));
        RunRecord run=run("SUCCEEDED");EmailAttachmentFixtures.screenshot(run,"result-page");notifier.afterRun(run,input(List.of("one@example.test")));assertThat(run.status).isEqualTo("SUCCEEDED");assertThat(outcome(run).get("status")).isEqualTo("FAILED");
        verify(sender,times(1)).send(any(MimeMessage.class));assertThat(run.view().toString()).doesNotContain("private SMTP server feedback");
    }
    @Test void attachmentFailureStopsBeforeAnyEmailIsSent()throws Exception{
        JavaMailSender sender=mock(JavaMailSender.class);RunRecord missing=run("SUCCEEDED");
        new SuccessNotifier(sender,"sender@example.test",true).afterRun(missing,input(List.of("one@example.test")));
        assertThat(outcome(missing).get("status")).isEqualTo("ATTACHMENT_FAILED");assertThat(missing.status).isEqualTo("SUCCEEDED");verifyNoInteractions(sender);
        RunRecord large=run("SUCCEEDED");EmailAttachmentFixtures.screenshot(large,"result-page");new SuccessNotifier(sender,"sender@example.test",true,1).afterRun(large,input(List.of("one@example.test")));
        assertThat(outcome(large).get("status")).isEqualTo("ATTACHMENT_FAILED");verifyNoInteractions(sender);
    }
    private static void collect(Part part,List<BodyPart> result)throws Exception{
        if(part.isMimeType("multipart/*")){Multipart multipart=(Multipart)part.getContent();for(int i=0;i<multipart.getCount();i++){BodyPart child=multipart.getBodyPart(i);if(child.isMimeType("multipart/*"))collect(child,result);else result.add(child);}}
    }
}
