package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.io.IOException;

/** Local AES-GCM persistence with atomic replacement. The key and encrypted data stay outside version control. */
final class ScheduleStore {
    private final Path directory,file,keyFile;
    private final ObjectMapper mapper;
    private final SecureRandom random=new SecureRandom();
    ScheduleStore(Path directory,ObjectMapper mapper){this.directory=directory.toAbsolutePath();this.file=this.directory.resolve("schedules.enc");this.keyFile=this.directory.resolve("schedule.key");this.mapper=mapper;}
    List<SavedSchedule> load() {
        if(!Files.exists(file))return new ArrayList<>();
        try {
            byte[] bytes=Files.readAllBytes(file);
            if(bytes.length<29)throw new IOException("Invalid encrypted schedule store");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key(false),"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(bytes,0,12)));
            byte[] json=cipher.doFinal(Arrays.copyOfRange(bytes,12,bytes.length));
            return new ArrayList<>(mapper.readValue(json,mapper.getTypeFactory().constructCollectionType(List.class,SavedSchedule.class)));
        }catch(Exception e){throw new IllegalStateException("Cannot open saved schedules. Preserve schedules.enc and schedule.key; no schedules were launched",e);}
    }
    void save(Collection<SavedSchedule> records) {
        try {
            Files.createDirectories(directory);byte[] nonce=new byte[12];random.nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key(true),"AES"),new GCMParameterSpec(128,nonce));
            byte[] encrypted=cipher.doFinal(mapper.writeValueAsBytes(records));byte[] bytes=new byte[nonce.length+encrypted.length];System.arraycopy(nonce,0,bytes,0,nonce.length);System.arraycopy(encrypted,0,bytes,nonce.length,encrypted.length);
            Path temp=Files.createTempFile(directory,"schedules-",".tmp");
            try {Files.write(temp,bytes);try {Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}
            finally {Files.deleteIfExists(temp);}
        }catch(Exception e){throw new IllegalStateException("Could not save schedules; check local storage permissions",e);}
    }
    private byte[] key(boolean create)throws IOException {
        if(Files.exists(keyFile)){byte[] key=Files.readAllBytes(keyFile);if(key.length!=32)throw new IOException("Invalid schedule encryption key");return key;}
        if(!create)throw new IOException("Schedule encryption key is missing");
        byte[] key=new byte[32];random.nextBytes(key);Files.write(keyFile,key,StandardOpenOption.CREATE_NEW);return key;
    }
}
