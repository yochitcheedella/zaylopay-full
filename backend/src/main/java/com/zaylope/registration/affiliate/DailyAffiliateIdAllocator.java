package com.zaylope.registration.affiliate;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates daily sequential Affiliate ID counter values in a dedicated short REQUIRES_NEW
 * transaction so InnoDB row locks are committed and released before another caller acquires them,
 * and transient deadlock retries occur cleanly on fresh transactions.
 */
@Service
public class DailyAffiliateIdAllocator {

	private final DailyAffiliateIdCounterRepository counterRepository;

	public DailyAffiliateIdAllocator(DailyAffiliateIdCounterRepository counterRepository) {
		this.counterRepository = counterRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int allocateSerial(LocalDate businessDate) {
		counterRepository.initializeCounterIfAbsent(businessDate);
		DailyAffiliateIdCounter counter = counterRepository.findByCounterDateWithLock(businessDate)
				.orElseGet(() -> counterRepository.saveAndFlush(new DailyAffiliateIdCounter(businessDate, 0)));
		int serial = counter.incrementSerial();
		counterRepository.saveAndFlush(counter);
		return serial;
	}
}
