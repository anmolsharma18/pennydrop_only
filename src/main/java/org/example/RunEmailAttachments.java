package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class RunEmailAttachments {
    static final long DEFAULT_LIMIT=18L*1024*1024;
    record Attachment(String name,byte[] bytes,String contentType){}
    record Prepared(List<Attachment> attachments,List<String> notes){}
    static Prepared prepare(RunRecord run,long limit){
        List<Attachment> result=new ArrayList<>();List<String> notes=new ArrayList<>();long total=0;
        byte[] csv=ApiResponseCsv.create(run);
        if(csv.length<=limit){result.add(new Attachment("api-responses-"+run.id+".csv",csv,ApiResponseCsv.MIME));total=csv.length;}
        else notes.add("API response CSV exceeds the attachment size limit; report remains available in the portal.");
        if(run.screenshots().isEmpty())notes.add("No screenshots were captured before the run ended.");
        for(var screenshot:run.screenshots()){
            String label=screenshot.get("label");Path file=RunScreenshots.path(run.id,label);
            try{
                if(!Files.isRegularFile(file)||Files.size(file)==0){notes.add(label+".png is unavailable.");continue;}
                if(Files.size(file)>limit-total){notes.add(label+".png exceeds the remaining attachment size limit.");continue;}
                byte[] bytes=Files.readAllBytes(file);
                if(bytes.length>limit-total){notes.add(label+".png exceeds the remaining attachment size limit.");continue;}
                total+=bytes.length;result.add(new Attachment(label+".png",bytes,"image/png"));
            }catch(IOException e){notes.add(label+".png could not be read.");}
        }
        return new Prepared(List.copyOf(result),List.copyOf(notes));
    }
}
