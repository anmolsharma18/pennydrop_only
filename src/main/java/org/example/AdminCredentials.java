package org.example;

import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Credentials stay outside source control and reports. Local key access also permits decryption. */
final class AdminCredentials {
    private final String username,password;
    AdminCredentials(String username,String password){this.username=username;this.password=password;}
    String username(){return username;} String password(){return password;}
    @Override public String toString(){return "AdminCredentials[redacted]";}
    static AdminCredentials load(Path directory) {
        String user=System.getenv("KYC_ADMIN_USERNAME"),pass=System.getenv("KYC_ADMIN_PASSWORD");
        if(user!=null&&!user.isBlank()&&pass!=null&&!pass.isBlank())return new AdminCredentials(user,pass);
        return readLocal(directory);
    }
    static AdminCredentials readLocal(Path directory) {
        try {
            byte[] key=Files.readAllBytes(directory.resolve("credentials.key")),bytes=Files.readAllBytes(directory.resolve("credentials.enc"));
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(bytes,0,12)));
            try(var in=new DataInputStream(new ByteArrayInputStream(cipher.doFinal(Arrays.copyOfRange(bytes,12,bytes.length))))){return new AdminCredentials(in.readUTF(),in.readUTF());}
        }catch(Exception e){throw new IllegalStateException("Admin credentials unavailable. Configure KYC_ADMIN_USERNAME and KYC_ADMIN_PASSWORD or restore the local encrypted credentials and key.");}
    }
    static void save(Path directory,String username,String password)throws Exception {
        if(username==null||username.isBlank()||password==null||password.isBlank())throw new IllegalArgumentException("Provide admin credentials using environment variables");
        Files.createDirectories(directory);Path keyFile=directory.resolve("credentials.key");SecureRandom random=new SecureRandom();
        byte[] key;if(Files.exists(keyFile))key=Files.readAllBytes(keyFile);else{key=new byte[32];random.nextBytes(key);Files.write(keyFile,key,StandardOpenOption.CREATE_NEW);}
        byte[] nonce=new byte[12];random.nextBytes(nonce);var output=new ByteArrayOutputStream();try(var out=new DataOutputStream(output)){out.writeUTF(username);out.writeUTF(password);}
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        byte[] encrypted=cipher.doFinal(output.toByteArray()),bytes=new byte[nonce.length+encrypted.length];System.arraycopy(nonce,0,bytes,0,nonce.length);System.arraycopy(encrypted,0,bytes,nonce.length,encrypted.length);
        Path temp=Files.createTempFile(directory,"credentials-",".tmp");try{Files.write(temp,bytes);try{Files.move(temp,directory.resolve("credentials.enc"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,directory.resolve("credentials.enc"),StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
    }
    public static void main(String[] args)throws Exception {save(Path.of(args.length==0?"data/admin":args[0]),System.getenv("KYC_ADMIN_USERNAME"),System.getenv("KYC_ADMIN_PASSWORD"));System.out.println("Admin credentials saved locally (encrypted); values omitted");}
}
