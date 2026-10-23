package org.example;

import jakarta.mail.internet.InternetAddress;
import java.util.*;

final class EmailRecipients {
    static List<String> parse(String raw){
        if(raw==null||raw.isBlank())return List.of();
        List<String> values=Arrays.stream(raw.split("[,;\\r\\n]+" )).map(String::trim).filter(s->!s.isEmpty()).toList();
        if(!valid(values))throw new IllegalArgumentException("Enter at most 20 valid email addresses separated by commas, semicolons or new lines");
        return unique(values);
    }
    static boolean valid(List<String> addresses){
        if(addresses==null||addresses.size()>20)return false;
        for(String address:addresses){
            if(address==null||address.length()>254||!address.matches("[^\\s,;<>]+@[^\\s,;<>]+"))return false;
            try{InternetAddress parsed=new InternetAddress(address,true);parsed.validate();if(parsed.getPersonal()!=null||!address.equals(parsed.getAddress()))return false;}
            catch(Exception e){return false;}
        }
        return true;
    }
    static List<String> unique(List<String> values){var result=new LinkedHashMap<String,String>();for(String s:values)result.putIfAbsent(s.toLowerCase(Locale.ROOT),s);return List.copyOf(result.values());}
}
