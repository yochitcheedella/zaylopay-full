package com.zaylope.registration.affiliate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.CannotAcquireLockException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AffiliateIdServiceTest {

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	private DailyAffiliateIdCounterRepository counterRepository;
	private AffiliateMembershipRepository membershipRepository;
	private AffiliateIdService service;

	@BeforeEach
	void setUp() {
		counterRepository = Mockito.mock(DailyAffiliateIdCounterRepository.class);
		membershipRepository = Mockito.mock(AffiliateMembershipRepository.class);
		when(counterRepository.saveAndFlush(any(DailyAffiliateIdCounter.class))).thenAnswer(i -> i.getArgument(0));
		service = new AffiliateIdService(
				counterRepository,
				membershipRepository,
				Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), IST));
	}

	@Test
	void shouldGenerateAffiliateIdInRequiredFormatUsingAsiaKolkataDate() {
		LocalDate businessDate = LocalDate.of(2026, 11, 26);
		DailyAffiliateIdCounter counter = new DailyAffiliateIdCounter(businessDate, 0);

		when(counterRepository.findByCounterDateWithLock(businessDate)).thenReturn(Optional.of(counter));

		String id1 = service.generateNextAffiliateId();
		String id2 = service.generateNextAffiliateId();

		assertEquals("ZP-261126-001", id1);
		assertEquals("ZP-261126-002", id2);
		verify(counterRepository, times(2)).initializeCounterIfAbsent(businessDate);
	}

	@Test
	void shouldRollOverDailyDateUsingAsiaKolkataMidnightBoundary() {
		// 2026-11-26T18:29:59Z == 2026-11-26 23:59:59 IST
		Clock beforeMidnightIst = Clock.fixed(Instant.parse("2026-11-26T18:29:59Z"), IST);
		// 2026-11-26T18:30:01Z == 2026-11-27 00:00:01 IST (already next day in IST!)
		Clock afterMidnightIst = Clock.fixed(Instant.parse("2026-11-26T18:30:01Z"), IST);

		LocalDate nov26 = LocalDate.of(2026, 11, 26);
		LocalDate nov27 = LocalDate.of(2026, 11, 27);
		when(counterRepository.findByCounterDateWithLock(nov26))
				.thenReturn(Optional.of(new DailyAffiliateIdCounter(nov26, 4)));
		when(counterRepository.findByCounterDateWithLock(nov27))
				.thenReturn(Optional.of(new DailyAffiliateIdCounter(nov27, 0)));

		AffiliateIdService beforeService = new AffiliateIdService(counterRepository, membershipRepository, beforeMidnightIst);
		AffiliateIdService afterService = new AffiliateIdService(counterRepository, membershipRepository, afterMidnightIst);

		assertEquals("ZP-261126-005", beforeService.generateNextAffiliateId());
		assertEquals("ZP-261127-001", afterService.generateNextAffiliateId());
	}

	@Test
	void shouldSupportDailySequenceNumbersBeyond999() {
		LocalDate businessDate = LocalDate.of(2026, 11, 26);
		DailyAffiliateIdCounter counter = new DailyAffiliateIdCounter(businessDate, 998);
		when(counterRepository.findByCounterDateWithLock(businessDate)).thenReturn(Optional.of(counter));

		assertEquals("ZP-261126-999", service.generateNextAffiliateId());
		assertEquals("ZP-261126-1000", service.generateNextAffiliateId());
		assertEquals("ZP-261126-1001", service.generateNextAffiliateId());
	}

	@Test
	void shouldRetryBoundedTransientLockFailureAndSucceed() {
		LocalDate businessDate = LocalDate.of(2026, 11, 26);
		DailyAffiliateIdCounter counter = new DailyAffiliateIdCounter(businessDate, 0);

		when(counterRepository.findByCounterDateWithLock(businessDate))
				.thenThrow(new CannotAcquireLockException("Transient deadlock"))
				.thenReturn(Optional.of(counter));

		assertEquals("ZP-261126-001", service.generateNextAffiliateId());
	}

	@Test
	void shouldGenerateUniqueReferralCode() {
		when(membershipRepository.existsByReferralCode(any())).thenReturn(false);

		String code1 = service.generateUniqueReferralCode();
		String code2 = service.generateUniqueReferralCode();

		assertNotNull(code1);
		assertNotNull(code2);
		assertTrue(code1.startsWith("ZP"));
		assertEquals(8, code1.length());
		assertNotEquals(code1, code2);
	}

	@Test
	void shouldGenerateSequentialIdsUnderConcurrency() throws InterruptedException {
		LocalDate today = LocalDate.of(2026, 11, 26);
		AtomicInteger atomicCounter = new AtomicInteger(0);

		when(counterRepository.findByCounterDateWithLock(today)).thenAnswer(inv -> {
			DailyAffiliateIdCounter c = Mockito.mock(DailyAffiliateIdCounter.class);
			when(c.incrementSerial()).thenAnswer(i -> atomicCounter.incrementAndGet());
			return Optional.of(c);
		});

		int threadCount = 24;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(threadCount);
		Set<String> generatedIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					latch.await();
					generatedIds.add(service.generateNextAffiliateId());
				} catch (Exception ignored) {
				} finally {
					doneLatch.countDown();
				}
			});
		}

		latch.countDown();
		boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
		executor.shutdown();

		assertTrue(finished);
		assertEquals(threadCount, generatedIds.size());
		assertEquals(threadCount, atomicCounter.get());
	}
}
