package org.example;

import jakarta.validation.constraints.*;

/** Only the fields needed to identify an existing admin entry and choose the browser. */
public record DeleteInput(
        @NotBlank @Pattern(regexp="[6-9][0-9]{9}") String mobile,
        @NotBlank @Size(max=150) String name,
        @NotBlank @Pattern(regexp="[A-Z]{4}0[A-Z0-9]{6}") String ifsc,
        @NotBlank @Pattern(regexp="[0-9]{1,34}") String accountNumber,
        @NotNull RunInput.BrowserMode browserMode,java.util.List<String> notificationEmails) {
    public DeleteInput {notificationEmails=notificationEmails==null?java.util.List.of():java.util.List.copyOf(notificationEmails);}
    public DeleteInput(String mobile,String name,String ifsc,String accountNumber,RunInput.BrowserMode browserMode){this(mobile,name,ifsc,accountNumber,browserMode,java.util.List.of());}
    @AssertTrue(message="Enter at most 20 valid notification email addresses")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNotificationEmailsValid(){return EmailRecipients.valid(notificationEmails);}
    static DeleteInput from(RunInput input){return new DeleteInput(input.mobile(),input.name(),input.ifsc(),input.accountNumber(),input.browserMode(),input.notificationEmails());}
}
