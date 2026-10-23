package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Separate encrypted profile: never expires or changes as a side effect of completing a run. */
@Service
public class SavedDetailsService {
    static final Set<String> FIELDS=Set.of("mobile","dependency","name","ifsc","accountNumber","confirmAccountNumber","accountType","otpMode","fixedOtp","browserMode","scheduledLocalTime","notificationEmails");
    private final Path directory,file,keyFile;
    private final ObjectMapper mapper;
    public SavedDetailsService(@Value("${portal.profile-directory:data/profile}") String directory,ObjectMapper mapper){
        this.directory=Path.of(directory).toAbsolutePath();file=this.directory.resolve("profile.enc");keyFile=this.directory.resolve("profile.key");this.mapper=mapper;
    }
    public synchronized Map<String,String> load(){
        if(!Files.exists(file))return Map.of();
        try{
            byte[] bytes=Files.readAllBytes(file);if(bytes.length<29)throw new IllegalStateException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Files.readAllBytes(keyFile),"AES"),new GCMParameterSpec(128,Arrays.copyOf(bytes,12)));
            return mapper.readValue(cipher.doFinal(Arrays.copyOfRange(bytes,12,bytes.length)),mapper.getTypeFactory().constructMapType(LinkedHashMap.class,String.class,String.class));
        }catch(Exception e){throw new IllegalStateException("Cannot read saved details. Preserve profile.enc and profile.key; saved details were not cleared",e);}
    }
    public synchronized Map<String,String> save(Map<String,String> values){
        if(values==null||!FIELDS.containsAll(values.keySet())||values.values().stream().anyMatch(v->v==null||v.length()>6000))throw bad("Invalid saved input fields");
        try{EmailRecipients.parse(values.get("notificationEmails"));}catch(IllegalArgumentException e){throw bad(e.getMessage());}
        if(values.containsKey("otpMode")&&!Set.of("RANDOM","FIXED").contains(values.get("otpMode")))throw bad("Invalid OTP mode");
        if(values.containsKey("browserMode")&&!Set.of("HEADLESS","VISIBLE").contains(values.get("browserMode")))throw bad("Invalid browser mode");
        try{
            Files.createDirectories(directory);
            if(!Files.exists(keyFile)){byte[] key=new byte[32];new SecureRandom().nextBytes(key);Files.write(keyFile,key,StandardOpenOption.CREATE_NEW);}
            byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(Files.readAllBytes(keyFile),"AES"),new GCMParameterSpec(128,nonce));
            byte[] encrypted=cipher.doFinal(mapper.writeValueAsBytes(values));byte[] bytes=Arrays.copyOf(nonce,12+encrypted.length);System.arraycopy(encrypted,0,bytes,12,encrypted.length);
            Path temp=Files.createTempFile(directory,"profile-",".tmp");
            try{Files.write(temp,bytes);try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}
            finally{Files.deleteIfExists(temp);}
            return Map.copyOf(values);
        }catch(Exception e){throw new IllegalStateException("Cannot save details; previous saved details were not intentionally cleared",e);}
    }
    public synchronized Map<String,String> clear(){
        var empty=new LinkedHashMap<String,String>();FIELDS.forEach(key->empty.put(key,""));empty.put("otpMode","RANDOM");empty.put("browserMode","VISIBLE");return save(empty);
    }
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
