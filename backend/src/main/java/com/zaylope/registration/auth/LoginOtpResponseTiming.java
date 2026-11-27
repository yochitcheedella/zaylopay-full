package com.zaylope.registration.auth;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** Pads public issuance responses outside the transaction, hiding ordinary lookup/write latency. */
final class LoginOtpResponseTiming {

	static final long MIN_NANOS = 400_000_000L;
	static final long JITTER_NANOS = 100_000_000L;
	private final LongSupplier nanoTime;
	private final LongConsumer pause;
	private final long deadline;

	static LoginOtpResponseTiming start() {
		return new LoginOtpResponseTiming(System::nanoTime, LockSupport::parkNanos,
				ThreadLocalRandom.current().nextLong(JITTER_NANOS));
	}

	LoginOtpResponseTiming(LongSupplier nanoTime, LongConsumer pause, long jitter) {
		this.nanoTime = nanoTime;
		this.pause = pause;
		this.deadline = nanoTime.getAsLong() + MIN_NANOS + jitter;
	}

	void awaitDeadline() {
		long remaining;
		while ((remaining = deadline - nanoTime.getAsLong()) > 0 && !Thread.currentThread().isInterrupted()) {
			pause.accept(remaining);
		}
	}
}
