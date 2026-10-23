package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class RunEmailAttachments {
    static final long DEFAULT_LIMIT=18L*1024*1024;
    record Attachment(String name,byte[] bytes,String contentType){}
    static List<Attachment> prepare(RunRecord run,long limit)throws IOException{
        List<Attachment> result=new ArrayList<>();byte[] excel=ApiResponseWorkbook.create(run);long total=excel.length;
        if(total>limit)throw new IOException("Attachment size limit exceeded");
        result.add(new Attachment("api-responses-"+run.id+".xlsx",excel,ApiResponseWorkbook.MIME));
        if(run.screenshots().isEmpty())throw new IOException("Run screenshot is unavailable");
        for(var screenshot:run.screenshots()){
            String label=screenshot.get("label");Path file=RunScreenshots.path(run.id,label);
            if(!Files.isRegularFile(file)||Files.size(file)==0)throw new IOException("Registered screenshot is unavailable");
            total+=Files.size(file);if(total>limit)throw new IOException("Attachment size limit exceeded");
            result.add(new Attachment(label+".png",Files.readAllBytes(file),"image/png"));
        }
        return List.copyOf(result);
    }
}
