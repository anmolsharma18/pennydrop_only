package org.example;

import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.mail.*;
import org.springframework.mail.javamail.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Service
public class SuccessNotifier {
    private final JavaMailSender sender;
    private final String from;
    private final boolean configured;
    private final long maxAttachmentBytes;
    @Autowired
    public SuccessNotifier(@Value("${portal.mail.host:}") String host,@Value("${portal.mail.port:587}") int port,
            @Value("${portal.mail.from:}") String from,@Value("${portal.mail.username:}") String username,
            @Value("${portal.mail.password:}") String password,@Value("${portal.mail.security:STARTTLS}") String security,
            @Value("${portal.mail.credentials-directory:data/mail}") String directory,@Value("${portal.mail.enabled:true}") boolean enabled,
            @Value("${portal.mail.max-attachment-bytes:18874368}") long maxAttachmentBytes){
        this(SmtpConfiguration.resolve(host,port,from,username,password,security,java.nio.file.Path.of(directory),enabled),maxAttachmentBytes);
    }
    private SuccessNotifier(SmtpConfiguration c,long limit){this(create(c.host(),c.port(),c.username(),c.password(),c.security()),c.from(),!c.host().isBlank()&&EmailRecipients.valid(List.of(c.from())),limit);}
    SuccessNotifier(JavaMailSender sender,String from,boolean configured){this(sender,from,configured,RunEmailAttachments.DEFAULT_LIMIT);}
    SuccessNotifier(JavaMailSender sender,String from,boolean configured,long limit){this.sender=sender;this.from=from;this.configured=configured;this.maxAttachmentBytes=limit;}
    static JavaMailSenderImpl create(String host,int port,String username,String password,String security){
        if(!Set.of("STARTTLS","SSL","NONE").contains(security))throw new IllegalArgumentException("SMTP security must be STARTTLS, SSL or NONE");
        JavaMailSenderImpl mail=new JavaMailSenderImpl();mail.setHost(host);mail.setPort(port);mail.setUsername(username);mail.setPassword(password);mail.setDefaultEncoding("UTF-8");
        Properties p=mail.getJavaMailProperties();p.setProperty("mail.smtp.auth",String.valueOf(!username.isBlank()));
        p.setProperty("mail.smtp.starttls.enable",String.valueOf(security.equals("STARTTLS")));p.setProperty("mail.smtp.starttls.required",String.valueOf(security.equals("STARTTLS")));
        p.setProperty("mail.smtp.ssl.enable",String.valueOf(security.equals("SSL")));
        p.setProperty("mail.smtp.ssl.checkserveridentity","true");
        for(String key:List.of("connectiontimeout","timeout","writetimeout"))p.setProperty("mail.smtp."+key,"5000");
        return mail;
    }
    public boolean configured(){return configured;}
    void afterRun(RunRecord run,DeleteInput input){
        List<String> recipients=EmailRecipients.unique(input.notificationEmails());
        if(recipients.isEmpty()){outcome(run,"SKIPPED",0,0,"No notification recipients selected");return;}
        if(!configured){outcome(run,"NOT_CONFIGURED",recipients.size(),0,"Run ended; email was not sent because SMTP is not configured");return;}
        List<RunEmailAttachments.Attachment> attachments;
        List<String> notes;
        try{var prepared=RunEmailAttachments.prepare(run,maxAttachmentBytes);attachments=prepared.attachments();notes=prepared.notes();}
        catch(RuntimeException e){attachments=List.of();notes=List.of("Attachments could not be prepared; review the run report in the portal.");}
        run.diagnostic("emailAttachmentNotes",notes);
        run.diagnostic("emailAttachments",Map.of("fileNames",attachments.stream().map(RunEmailAttachments.Attachment::name).toList(),"count",attachments.size(),"totalBytes",attachments.stream().mapToLong(a->a.bytes().length).sum()));
        int sent=0;
        for(String recipient:recipients){
            try{
            var message=sender.createMimeMessage();var helper=new MimeMessageHelper(message,true,"UTF-8");helper.setFrom(from);helper.setTo(recipient);
            helper.setSubject("KYC automation "+run.status.toLowerCase(Locale.ROOT)+" - "+run.id);
            helper.setText(("SUCCEEDED".equals(run.status)?"Automation completed successfully.":"Automation ended with status: "+run.status+".")+"\nRun: "+run.id+"\nType: "+run.kind+"\nFinished: "+run.finishedAt+
                "\nWorkflow stage: "+run.workflowStage+
                (run.error==null?"":"\nFailure: "+run.error)+
                "\nApplicant: "+input.name()+"\nMobile: "+mask(input.mobile())+"\nAccount: "+mask(input.accountNumber())+
                "\nIFSC: "+input.ifsc()+"\nBrowser request hops captured: "+run.network.snapshot().size()+
                "\nAvailable run screenshots (PNG) and captured API response table (CSV) are attached."+
                "\nCSV includes observed status codes and network errors; response bodies are not captured."+
                (notes.isEmpty()?"":"\nAttachment notes:\n"+String.join("\n",notes))+"\n");
            for(var attachment:attachments)helper.addAttachment(attachment.name(),new ByteArrayResource(attachment.bytes()),attachment.contentType());
            sender.send(message);sent++;
            }catch(MailException|jakarta.mail.MessagingException e){/* Do not expose server responses, credentials, or recipient addresses. No automatic retry. */}
        }
        outcome(run,sent==recipients.size()?"ACCEPTED_BY_SMTP":sent>0?"PARTIAL":"FAILED",recipients.size(),sent,
            sent==recipients.size()?"Run notifications accepted by SMTP; inbox delivery is not guaranteed":"Some run notifications were not accepted by SMTP; check sender settings. No automatic retry");
    }
    private static String mask(String value){return "****"+value.substring(Math.max(0,value.length()-4));}
    private static void outcome(RunRecord run,String status,int recipients,int accepted,String message){
        run.diagnostic("emailNotification",Map.of("status",status,"recipientCount",recipients,"acceptedCount",accepted,"message",message));
        if(!status.equals("SKIPPED"))run.stage(message);
    }
}
