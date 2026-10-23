package org.example;

import java.time.*;
import java.util.*;

final class SavedSchedule {
    public enum Status { PENDING, WAITING_FOR_RUNNER, DISPATCHING, RUNNING, SUCCEEDED, FAILED, CANCELLED, MISSED, INTERRUPTED }
    public String id,createdAt,scheduledLocalTime,runId,startedAt,finishedAt,message;
    public Instant scheduledAt;
    public RunInput input;
    public Status status;
    public Map<String,Object> report;
    public SavedSchedule() {}
    Map<String,Object> view() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",id);result.put("createdAt",createdAt);result.put("scheduledLocalTime",scheduledLocalTime);
        result.put("scheduledAt",scheduledAt.toString());result.put("timeZone","Asia/Kolkata");result.put("status",status);
        result.put("runId",runId);result.put("startedAt",startedAt);result.put("finishedAt",finishedAt);result.put("message",message);
        result.put("name",input.name());result.put("mobileMasked",mask(input.mobile()));result.put("accountMasked",mask(input.accountNumber()));
        result.put("browserMode",input.browserMode());result.put("otpMode",input.otpMode());
        return result;
    }
    private static String mask(String text){return text.length()<=4?"****":"*".repeat(text.length()-4)+text.substring(text.length()-4);}
}
