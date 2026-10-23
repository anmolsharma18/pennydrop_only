package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Requires exact run identity, or one fully matching applicant/bank record in the complete search result. */
final class AdminEntryMatcher {
    static String match(JsonNode response,RunInput input,String journeyIdentity) {
        return match(response,DeleteInput.from(input),journeyIdentity);
    }
    static String match(JsonNode response,DeleteInput input,String journeyIdentity) {
        if(response==null||!response.path("data").isArray())return null;
        List<JsonNode> matches=new ArrayList<>();
        for(JsonNode row:response.path("data")) {
            if(!"pennydrop_only".equals(text(row,"workflowName")))continue;
            String id=text(row,"identity");if(id.isBlank())continue;
            if(journeyIdentity!=null) {
                if(!id.equals(journeyIdentity))continue;
                for(var pair:Map.of("mobile_number",input.mobile(),"name",input.name(),"account_number",input.accountNumber(),"ifsc",input.ifsc()).entrySet())
                    if(row.hasNonNull(pair.getKey())&&!text(row,pair.getKey()).isBlank()&&!text(row,pair.getKey()).equals(pair.getValue().trim()))throw new IllegalStateException("Admin identity matched but applicant/bank details differ; deletion stopped");
            }else if(!text(row,"mobile_number").equals(input.mobile())||!text(row,"name").equals(input.name().trim())||!text(row,"account_number").equals(input.accountNumber())||!text(row,"ifsc").equals(input.ifsc()))continue;
            matches.add(row);
        }
        if(matches.size()>1)throw new IllegalStateException("Ambiguous maker-checker result: multiple matching journey entries. No deletion performed");
        if(matches.isEmpty())return null;
        if(journeyIdentity==null) {
            // Full-result uniqueness is essential; never delete a single visible match from a partial page.
            if(!response.path("total_counts").canConvertToInt()||response.path("total_counts").asInt()!=response.path("data").size())throw new IllegalStateException("Cannot uniquely correlate this run: search results are incomplete. No deletion performed");
        }
        return text(matches.get(0),"identity");
    }
    static String text(JsonNode row,String key){return row.path(key).isTextual()?row.path(key).asText().trim():"";}
    static String fingerprint(String identity){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8))).substring(0,16);}catch(Exception e){throw new IllegalStateException(e);}}
}
