package com.zaylope.registration.auth;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginOtpResponseTimingTest {
	@Test
	void fastLookupAndSlowerPersistenceHaveTheSameResponseDeadline() {
		for (long work : new long[] {1_000_000L, 150_000_000L}) {
			AtomicLong clock = new AtomicLong();
			LoginOtpResponseTiming timing = new LoginOtpResponseTiming(clock::get, clock::addAndGet, 50_000_000L);
			clock.addAndGet(work);
			timing.awaitDeadline();
			assertEquals(450_000_000L, clock.get());
		}
	}

	@Test
	void slowDatabaseDoesNotAddAnotherDelay() {
		AtomicLong clock = new AtomicLong();
		LoginOtpResponseTiming timing = new LoginOtpResponseTiming(clock::get, clock::addAndGet, 0);
		clock.addAndGet(900_000_000L);
		timing.awaitDeadline();
		assertEquals(900_000_000L, clock.get());
	}
}
