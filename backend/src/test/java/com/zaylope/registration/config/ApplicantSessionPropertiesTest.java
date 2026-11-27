package com.zaylope.registration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ApplicantSessionPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfig.class);

	@Configuration
	@EnableConfigurationProperties(ApplicantSessionProperties.class)
	static class TestConfig {}

	@Test
	void bindsDefaultValuesWhenPropertiesAreUnset() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ApplicantSessionProperties.class);
			ApplicantSessionProperties props = context.getBean(ApplicantSessionProperties.class);
			assertThat(props.cookieName()).isEqualTo("ZAYLOPE_APPLICANT");
			assertThat(props.lifetime()).isEqualTo(Duration.ofMinutes(30));
			assertThat(props.secureCookie()).isFalse();
			assertThat(props.onboardingCookieName()).isEqualTo("ZAYLOPE_ONBOARDING");
			assertThat(props.onboardingLifetime()).isEqualTo(Duration.ofMinutes(15));
		});
	}

	@Test
	void bindsCustomPropertiesViaCanonicalConstructor() {
		contextRunner
				.withPropertyValues(
						"applicant.session.cookie-name=CUSTOM_APPLICANT",
						"applicant.session.lifetime=45m",
						"applicant.session.secure-cookie=true",
						"applicant.session.onboarding-cookie-name=CUSTOM_ONBOARDING",
						"applicant.session.onboarding-lifetime=20m")
				.run(context -> {
					assertThat(context).hasSingleBean(ApplicantSessionProperties.class);
					ApplicantSessionProperties props = context.getBean(ApplicantSessionProperties.class);
					assertThat(props.cookieName()).isEqualTo("CUSTOM_APPLICANT");
					assertThat(props.lifetime()).isEqualTo(Duration.ofMinutes(45));
					assertThat(props.secureCookie()).isTrue();
					assertThat(props.onboardingCookieName()).isEqualTo("CUSTOM_ONBOARDING");
					assertThat(props.onboardingLifetime()).isEqualTo(Duration.ofMinutes(20));
				});
	}

	@Test
	void preservesThreeArgConvenienceConstructorDefaults() {
		ApplicantSessionProperties props = new ApplicantSessionProperties("TEST_COOKIE", Duration.ofMinutes(10), true);
		assertThat(props.cookieName()).isEqualTo("TEST_COOKIE");
		assertThat(props.lifetime()).isEqualTo(Duration.ofMinutes(10));
		assertThat(props.secureCookie()).isTrue();
		assertThat(props.onboardingCookieName()).isEqualTo("ZAYLOPE_ONBOARDING");
		assertThat(props.onboardingLifetime()).isEqualTo(Duration.ofMinutes(15));
	}
}
