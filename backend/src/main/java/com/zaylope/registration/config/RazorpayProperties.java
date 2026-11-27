package com.zaylope.registration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "razorpay")
public record RazorpayProperties(
		String keyId,
		String keySecret,
		String webhookSecret,
		long amountPaise,
		String currency,
		boolean mockGateway,
		String apiUrl) {

	public RazorpayProperties {
		if (keyId == null) {
			keyId = "";
		}
		if (keySecret == null) {
			keySecret = "";
		}
		if (webhookSecret == null) {
			webhookSecret = "";
		}
		if (amountPaise <= 0) {
			amountPaise = 59900L;
		}
		if (currency == null || currency.isBlank()) {
			currency = "INR";
		}
		if (apiUrl == null || apiUrl.isBlank()) {
			apiUrl = "https://api.razorpay.com/v1";
		}
	}
}
