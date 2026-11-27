package com.zaylope.registration.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaylope.registration.verification.ApplicantVerificationChannel;
import com.zaylope.registration.verification.EmailOtpDelivery;
import com.zaylope.registration.verification.MobileOtpDelivery;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class LoginOtpAfterCommitDeliveryTest {

	private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

	@Test
	void committedChallengePublishesDeliveryAfterCommit() {
		try (AnnotationConfigApplicationContext context =
				new AnnotationConfigApplicationContext(ListenerTestConfiguration.class)) {
			LoginOtpDeliveryStateService stateService = context.getBean(LoginOtpDeliveryStateService.class);
			EmailOtpDelivery emailDelivery = context.getBean(EmailOtpDelivery.class);
			QueueingExecutor executor = context.getBean(QueueingExecutor.class);
			when(stateService.findActiveUnexpiredChallengeExpiration(any()))
					.thenReturn(Optional.of(NOW.plusSeconds(300)));

			TransactionTemplate transactions =
					new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
			transactions.executeWithoutResult(status -> {
				context.publishEvent(new LoginOtpDispatchEvent(
						"committed-issuance",
						ApplicantVerificationChannel.EMAIL,
						"member@example.com",
						"123456"));
				assertEquals(0, executor.pendingTasks());
				verify(emailDelivery, never()).sendCode(any(), any());
			});

			assertEquals(1, executor.pendingTasks());
			executor.runPendingTasks();
			verify(emailDelivery).sendCode("member@example.com", "123456");
		}
	}

	@Test
	void rolledBackChallengeDoesNotPublishDelivery() {
		try (AnnotationConfigApplicationContext context =
				new AnnotationConfigApplicationContext(ListenerTestConfiguration.class)) {
			EmailOtpDelivery emailDelivery = context.getBean(EmailOtpDelivery.class);
			QueueingExecutor executor = context.getBean(QueueingExecutor.class);
			TransactionTemplate transactions =
					new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

			transactions.executeWithoutResult(status -> {
				context.publishEvent(new LoginOtpDispatchEvent(
						"rolled-back-issuance",
						ApplicantVerificationChannel.EMAIL,
						"member@example.com",
						"123456"));
				status.setRollbackOnly();
			});

			assertEquals(0, executor.pendingTasks());
			executor.runPendingTasks();
			verify(emailDelivery, never()).sendCode(any(), any());
		}
	}

	@Test
	void contactOtpDeliveryRunsOnlyAfterCommitAndIsSuppressedOnRollback() {
		try (AnnotationConfigApplicationContext context =
				new AnnotationConfigApplicationContext(ListenerTestConfiguration.class)) {
			var states = context.getBean(com.zaylope.registration.verification.ContactOtpDeliveryStateService.class);
			var email = context.getBean(EmailOtpDelivery.class);
			var executor = context.getBean(QueueingExecutor.class);
			when(states.expiration(any())).thenReturn(Optional.of(NOW.plusSeconds(300)));
			var event = new com.zaylope.registration.verification.ContactOtpDispatchEvent(
					71L, ApplicantVerificationChannel.EMAIL, "contact-issuance", "member@gmail.com", "123456");
			var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
			transactions.executeWithoutResult(status -> {
				context.publishEvent(event);
				verify(email, never()).sendCode(any(), any());
				assertEquals(0, executor.pendingTasks());
			});
			assertEquals(1, executor.pendingTasks());
			executor.runPendingTasks();
			verify(email).sendCode("member@gmail.com", "123456");
			org.mockito.Mockito.clearInvocations(email);
			transactions.executeWithoutResult(status -> { context.publishEvent(event); status.setRollbackOnly(); });
			executor.runPendingTasks();
			verify(email, never()).sendCode(any(), any());
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAsync
	@EnableTransactionManagement
	static class ListenerTestConfiguration {
		@Bean
		com.zaylope.registration.verification.ContactOtpDeliveryStateService contactStateService() {
			return mock(com.zaylope.registration.verification.ContactOtpDeliveryStateService.class);
		}
		@Bean
		com.zaylope.registration.verification.ContactOtpDeliveryDispatcher contactDispatcher(
				com.zaylope.registration.verification.ContactOtpDeliveryStateService states,
				MobileOtpDelivery mobile, EmailOtpDelivery email, Clock clock) {
			return new com.zaylope.registration.verification.ContactOtpDeliveryDispatcher(states, mobile, email, clock);
		}

		@Bean
		PlatformTransactionManager transactionManager() {
			return new TestTransactionManager();
		}

		@Bean("loginOtpExecutor")
		QueueingExecutor loginOtpExecutor() {
			return new QueueingExecutor();
		}

		@Bean
		LoginOtpDeliveryStateService deliveryStateService() {
			return mock(LoginOtpDeliveryStateService.class);
		}

		@Bean
		MobileOtpDelivery mobileOtpDelivery() {
			return mock(MobileOtpDelivery.class);
		}

		@Bean
		EmailOtpDelivery emailOtpDelivery() {
			return mock(EmailOtpDelivery.class);
		}

		@Bean
		Clock clock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}

		@Bean
		LoginOtpDeliveryDispatcher loginOtpDeliveryDispatcher(
				LoginOtpDeliveryStateService deliveryStateService,
				MobileOtpDelivery mobileOtpDelivery,
				EmailOtpDelivery emailOtpDelivery,
				Clock clock) {
			return new LoginOtpDeliveryDispatcher(
					deliveryStateService,
					mobileOtpDelivery,
					emailOtpDelivery,
					clock);
		}
	}

	private static class QueueingExecutor implements Executor {

		private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

		@Override
		public void execute(Runnable command) {
			tasks.add(command);
		}

		int pendingTasks() {
			return tasks.size();
		}

		void runPendingTasks() {
			Runnable task;
			while ((task = tasks.poll()) != null) {
				task.run();
			}
		}
	}

	private static class TestTransactionManager extends AbstractPlatformTransactionManager {

		@Override
		protected Object doGetTransaction() {
			return new Object();
		}

		@Override
		protected void doBegin(Object transaction, TransactionDefinition definition) {
		}

		@Override
		protected Object doSuspend(Object transaction) {
			return transaction;
		}

		@Override
		protected void doResume(Object transaction, Object suspendedResources) {
		}

		@Override
		protected void doCommit(DefaultTransactionStatus status) {
		}

		@Override
		protected void doRollback(DefaultTransactionStatus status) {
		}
	}
}
