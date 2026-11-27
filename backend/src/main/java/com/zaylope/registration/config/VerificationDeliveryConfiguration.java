package com.zaylope.registration.config;

import com.zaylope.registration.verification.EmailOtpDelivery;
import com.zaylope.registration.verification.MobileOtpDelivery;
import com.zaylope.registration.verification.SmtpEmailOtpDelivery;
import com.zaylope.registration.verification.VerificationDeliveryUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class VerificationDeliveryConfiguration {
    @Bean
    org.springframework.beans.factory.InitializingBean validateVerificationConfiguration(VerificationProperties properties,
            org.springframework.core.env.Environment environment) {
        return () -> {
            boolean mock = environment.getProperty("app.verification.mock-delivery", Boolean.class, false);
            boolean local = environment.matchesProfiles("dev", "test");
            boolean production = environment.matchesProfiles("prod", "production");
            if (mock && (!local || production)) throw new IllegalStateException("Mock OTP delivery requires only explicit dev/test profiles.");
            if ((properties.mobileEnabled() || properties.emailEnabled() || mock) && !properties.hasOtpHashKey())
                throw new IllegalStateException("Enabled OTP verification requires an environment OTP_HASH_KEY of at least 32 bytes.");
        };
    }
    @Bean
    @ConditionalOnProperty(name="app.verification.mock-delivery", havingValue="true")
    com.zaylope.registration.verification.DevelopmentOtpDelivery developmentOtpDelivery() {
        return new com.zaylope.registration.verification.DevelopmentOtpDelivery();
    }

	@Bean
	@ConditionalOnProperty(name="app.verification.mock-delivery", havingValue="false", matchIfMissing=true)
	@ConditionalOnMissingBean(MobileOtpDelivery.class)
	MobileOtpDelivery unconfiguredMobileOtpDelivery() {
		return (mobileNumber, code) -> {
			throw new VerificationDeliveryUnavailableException();
		};
	}

	@Bean
	@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("!${app.verification.email-enabled:false} and !${app.verification.mock-delivery:false}")
	@ConditionalOnMissingBean(EmailOtpDelivery.class)
	EmailOtpDelivery disabledEmailOtpDelivery() {
		return (emailAddress, code) -> {
			throw new VerificationDeliveryUnavailableException();
		};
	}

	@Bean
	@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${app.verification.email-enabled:false} and !${app.verification.mock-delivery:false}")
	@ConditionalOnMissingBean(EmailOtpDelivery.class)
	EmailOtpDelivery smtpEmailOtpDelivery(
			JavaMailSender mailSender,
			@Value("${app.verification.email-from:}") String fromAddress) {
		return new SmtpEmailOtpDelivery(mailSender, fromAddress);
	}
}

