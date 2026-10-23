package org.example;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SmtpConfigurationTest {
    @TempDir Path directory;
    @Test void persistsEncryptedSenderAndUsesItAcrossRestart()throws Exception{
        var settings=new SmtpConfiguration("smtp.gmail.com",587,"sender@example.test","sender@example.test","abcd efgh ijkl mnop","STARTTLS");settings.save(directory);
        var restored=SmtpConfiguration.resolve("",587,"","","","STARTTLS",directory,true);
        assertThat(restored.host()).isEqualTo("smtp.gmail.com");assertThat(restored.password()).isEqualTo("abcdefghijklmnop");
        assertThat(restored.from()).isEqualTo("sender@example.test");assertThat(restored.toString()).doesNotContain("abcd","sender");
        String bytes=new String(Files.readAllBytes(directory.resolve("credentials.enc")),java.nio.charset.StandardCharsets.ISO_8859_1);assertThat(bytes).doesNotContain("abcdefghijklmnop","sender@example.test");
        assertThat(SmtpConfiguration.resolve("",587,"","","","STARTTLS",directory,false).host()).isEmpty();
        assertThat(SmtpConfiguration.resolve("env.example.test",465,"env@example.test","env","other-password","SSL",directory,true).host()).isEqualTo("env.example.test");
        var mail=SuccessNotifier.create(restored.host(),restored.port(),restored.username(),restored.password(),restored.security());
        assertThat(mail.getJavaMailProperties()).containsEntry("mail.smtp.starttls.required","true").containsEntry("mail.smtp.auth","true").containsEntry("mail.smtp.ssl.checkserveridentity","true");
    }
}
