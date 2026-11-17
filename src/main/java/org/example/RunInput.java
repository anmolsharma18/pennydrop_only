package org.example;

import jakarta.validation.constraints.*;

public record RunInput(
        @NotBlank @Pattern(regexp="[6-9][0-9]{9}", message="must be ten digits beginning with 6–9 (format check only)") String mobile,
        @NotBlank @Size(max=100) String dependency,
        @NotBlank @Size(max=150) String name,
        @NotBlank @Pattern(regexp="[A-Z]{4}0[A-Z0-9]{6}", message="must match the basic IFSC format (format check only)") String ifsc,
        @NotBlank @Pattern(regexp="[0-9]{1,34}", message="must contain 1–34 digits") String accountNumber,
        @NotBlank String confirmAccountNumber,
        @NotBlank @Size(max=100) String accountType,
        @NotNull OtpMode otpMode,
        String fixedOtp,
        @NotNull BrowserMode browserMode,
        java.util.List<String> notificationEmails,
        @NotNull PennyDropType pennyDropType) {
    public RunInput {
        notificationEmails=notificationEmails==null?java.util.List.of():java.util.List.copyOf(notificationEmails);
        // Existing saved schedules and API clients predate tenant selection and remain Meon.
        pennyDropType=pennyDropType==null?PennyDropType.MEON:pennyDropType;
    }
    public RunInput(String mobile,String dependency,String name,String ifsc,String accountNumber,String confirmAccountNumber,String accountType,OtpMode otpMode,String fixedOtp,BrowserMode browserMode) {
        this(mobile,dependency,name,ifsc,accountNumber,confirmAccountNumber,accountType,otpMode,fixedOtp,browserMode,java.util.List.of(),PennyDropType.MEON);
    }
    public RunInput(String mobile,String dependency,String name,String ifsc,String accountNumber,String confirmAccountNumber,String accountType,OtpMode otpMode,String fixedOtp,BrowserMode browserMode,java.util.List<String> notificationEmails) {
        this(mobile,dependency,name,ifsc,accountNumber,confirmAccountNumber,accountType,otpMode,fixedOtp,browserMode,notificationEmails,PennyDropType.MEON);
    }
    @AssertTrue(message="Enter at most 20 valid notification email addresses")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNotificationEmailsValid(){return EmailRecipients.valid(notificationEmails);}
    public enum OtpMode { RANDOM, FIXED }
    public enum BrowserMode { HEADLESS, VISIBLE }
    public enum PennyDropType { MEON, RAZORPAY }
    @AssertTrue(message="Account number and confirmation must match")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isAccountConfirmationValid() { return accountNumber != null && accountNumber.equals(confirmAccountNumber); }
    @AssertTrue(message="Fixed OTP must contain exactly four digits")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isFixedOtpValid() { return otpMode != OtpMode.FIXED || (fixedOtp != null && fixedOtp.matches("[0-9]{4}")); }
}
