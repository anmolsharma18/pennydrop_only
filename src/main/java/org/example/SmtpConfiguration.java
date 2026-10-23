package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.Map;

/** Encrypted local sender settings; environment settings take precedence when a host is supplied. */
record SmtpConfiguration(String host,int port,String from,String username,String password,String security) {
    @Override public String toString(){return "SmtpConfiguration[redacted]";}
    static SmtpConfiguration resolve(String host,int port,String from,String username,String password,String security,Path directory,boolean enabled){
        if(!enabled)return new SmtpConfiguration("",port,"","","",security);
        if(host.isBlank()&&Files.exists(directory.resolve("credentials.enc")))return load(directory);
        return new SmtpConfiguration(host,port,from,username,normalizePassword(host,password),security);
    }
    private static String normalizePassword(String host,String password){return host.equalsIgnoreCase("smtp.gmail.com")?password.replaceAll("\\s",""):password;}
    void save(Path directory)throws Exception{
        if(host.isBlank()||port<1||port>65535||!EmailRecipients.valid(java.util.List.of(from))||username.isBlank()||password.isBlank()||!java.util.Set.of("STARTTLS","SSL","NONE").contains(security))throw new IllegalArgumentException("Incomplete or invalid SMTP configuration");
        String settings=new ObjectMapper().writeValueAsString(Map.of("host",host,"port",port,"from",from,"username",username,"security",security));
        AdminCredentials.save(directory,settings,normalizePassword(host,password));
    }
    static SmtpConfiguration load(Path directory){
        try{
            AdminCredentials stored=AdminCredentials.readLocal(directory);var json=new ObjectMapper().readTree(stored.username());
            return new SmtpConfiguration(json.path("host").asText(),json.path("port").asInt(),json.path("from").asText(),json.path("username").asText(),stored.password(),json.path("security").asText());
        }catch(Exception e){throw new IllegalStateException("Cannot load encrypted SMTP configuration; preserve data/mail credentials and key");}
    }
    public static void main(String[] args){
        Path directory=Path.of(args.length>1?args[1]:"data/mail");
        try{
            if(args.length>0&&args[0].equals("save")){
                new SmtpConfiguration(env("PORTAL_SMTP_HOST"),Integer.parseInt(System.getenv().getOrDefault("PORTAL_SMTP_PORT","587")),env("PORTAL_SMTP_FROM"),env("PORTAL_SMTP_USERNAME"),env("PORTAL_SMTP_PASSWORD"),System.getenv().getOrDefault("PORTAL_SMTP_SECURITY","STARTTLS")).save(directory);
                System.out.println("SMTP settings saved locally (encrypted); credential values omitted");
            }else if(args.length>0&&args[0].equals("check")){
                SmtpConfiguration c=load(directory);SuccessNotifier.create(c.host,c.port,c.username,c.password,c.security).testConnection();
                System.out.println("SMTP connection and authentication verified. No email was sent.");
            }else throw new IllegalArgumentException();
        }catch(Exception e){
            System.err.println("SMTP configuration/check failed ("+e.getClass().getSimpleName()+"). Check sender settings, network access and provider permissions; credentials omitted.");System.exit(1);
        }
    }
    private static String env(String key){return System.getenv().getOrDefault(key,"");}
}
