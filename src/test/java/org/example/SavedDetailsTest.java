package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SavedDetailsTest {
    @TempDir Path directory;
    @Test void persistsEncryptedStringsAcrossRestartAndOnlyClearsExplicitly()throws Exception{
        var store=new SavedDetailsService(directory.toString(),new ObjectMapper());
        var values=Map.of("mobile","6999999999","accountNumber","0000123","fixedOtp","0007","notificationEmails","one@example.test; two@example.test","otpMode","FIXED");
        store.save(values);assertThat(new SavedDetailsService(directory.toString(),new ObjectMapper()).load()).isEqualTo(values);
        assertThat(new String(Files.readAllBytes(directory.resolve("profile.enc")),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("one@example.test","6999999999","0000123");
        store.save(Map.of("notificationEmails","new@example.test","accountNumber","0000999"));
        assertThat(store.load()).containsEntry("accountNumber","0000999").containsEntry("notificationEmails","new@example.test");
        store.clear();assertThat(new SavedDetailsService(directory.toString(),new ObjectMapper()).load()).containsEntry("notificationEmails","").containsEntry("accountNumber","").containsEntry("mobile","");
    }
    @Test void invalidRecipientsCannotOverwriteSavedDetails(){
        var store=new SavedDetailsService(directory.toString(),new ObjectMapper());store.save(Map.of("notificationEmails","qa@example.test"));
        for(String value:List.of("invalid","a@example.test\r\nBcc: b@example.test","User <a@example.test>"))
            assertThatThrownBy(()->store.save(Map.of("notificationEmails",value))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(store.load()).containsEntry("notificationEmails","qa@example.test");
        assertThatThrownBy(()->store.save(Map.of("unknown","secret"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(EmailRecipients.parse("a@example.test; A@example.test\nb@example.test")).containsExactly("a@example.test","b@example.test");
    }
    @Test void damagedStoreIsNeverSilentlyReset()throws Exception{
        var store=new SavedDetailsService(directory.toString(),new ObjectMapper());store.save(Map.of("name","Fixture"));
        byte[] bytes=Files.readAllBytes(directory.resolve("profile.enc"));bytes[15]^=1;Files.write(directory.resolve("profile.enc"),bytes);
        assertThatThrownBy(store::load).hasMessageContaining("saved details were not cleared");assertThat(Files.readAllBytes(directory.resolve("profile.enc"))).isEqualTo(bytes);
    }
}
