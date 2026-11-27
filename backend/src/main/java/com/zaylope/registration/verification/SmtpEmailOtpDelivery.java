package com.zaylope.registration.verification;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SmtpEmailOtpDelivery implements EmailOtpDelivery {

	private final JavaMailSender mailSender;
	private final String fromAddress;

	public SmtpEmailOtpDelivery(JavaMailSender mailSender, String fromAddress) {
		this.mailSender = mailSender;
		this.fromAddress = fromAddress;
	}

	@Override
	public void sendCode(String emailAddress, String code) {
		if (fromAddress == null || fromAddress.isBlank()) {
			throw new VerificationDeliveryUnavailableException();
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(fromAddress);
		message.setTo(emailAddress);
		message.setSubject("Your ZAYLOPE verification code");
		message.setText("Your six-digit verification code is " + code
				+ ". It expires in five minutes. Do not share this code with anyone.");
		try {
			mailSender.send(message);
		} catch (MailException exception) {
			throw new VerificationDeliveryUnavailableException();
		}
	}
}
