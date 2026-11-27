package com.zaylope.registration.config;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
class VerificationDeliveryConfigurationTest {
    private ApplicationContextRunner runner(String profile,String secret) {
        return new ApplicationContextRunner().withUserConfiguration(VerificationDeliveryConfiguration.class)
            .withInitializer(context->context.getEnvironment().setActiveProfiles(profile))
            .withPropertyValues("app.verification.mock-delivery=true","app.verification.email-enabled=true")
            .withBean(VerificationProperties.class,()->new VerificationProperties(true,true,secret,Duration.ofMinutes(5),Duration.ofMinutes(1),Duration.ofHours(1),5,5));
    }
    @Test void productionRejectsMockDelivery(){runner("prod","test-only-strong-key-32-bytes-or-more").run(context->assertThat(context).hasFailed());}
    @Test void productionAliasRejectsMockDelivery(){runner("production","test-only-strong-key-32-bytes-or-more").run(context->assertThat(context).hasFailed());}
    @Test void noExplicitLocalProfileRejectsMockDelivery(){runner("default","test-only-strong-key-32-bytes-or-more").run(context->assertThat(context).hasFailed());}
    @Test void enabledVerificationRejectsMissingSecret(){runner("test","").run(context->assertThat(context).hasFailed());}
    @Test void explicitDevelopmentAllowsMockWithoutSmtp(){runner("dev","test-only-strong-key-32-bytes-or-more").run(context->{assertThat(context).hasNotFailed();assertThat(context).hasSingleBean(com.zaylope.registration.verification.DevelopmentOtpDelivery.class);});}
}
