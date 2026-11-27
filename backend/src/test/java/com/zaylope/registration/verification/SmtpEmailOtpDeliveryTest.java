package com.zaylope.registration.verification;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SmtpEmailOtpDeliveryTest {
    @Test void providerFailureHasNoSensitiveErrorDetail() {
        JavaMailSender sender=mock(JavaMailSender.class);
        doThrow(new MailSendException("Provider detail must not escape")).when(sender).send(any(SimpleMailMessage.class));
        var adapter=new SmtpEmailOtpDelivery(sender,"verification@example.test");
        var failure=assertThrows(VerificationDeliveryUnavailableException.class,()->adapter.sendCode("applicant@example.test","001234"));
        assertNull(failure.getCause());assertNull(failure.getMessage());
    }
}
