package com.zaylope.registration.affiliate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

@Service
public class AffiliateIdService {

	public static final ZoneId BUSINESS_TIME_ZONE = ZoneId.of("Asia/Kolkata");
	private static final String REFERRAL_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
	private static final int MAX_LOCK_RETRIES = 5;

	private final DailyAffiliateIdCounterRepository counterRepository;
	private final DailyAffiliateIdAllocator counterAllocator;
	private final AffiliateMembershipRepository membershipRepository;
	private final Clock clock;
	private final Object counterLock = new Object();

	public AffiliateIdService(
			DailyAffiliateIdCounterRepository counterRepository,
			AffiliateMembershipRepository membershipRepository) {
		this(counterRepository, new DailyAffiliateIdAllocator(counterRepository), membershipRepository, Clock.systemUTC());
	}

	public AffiliateIdService(
			DailyAffiliateIdCounterRepository counterRepository,
			AffiliateMembershipRepository membershipRepository,
			Clock clock) {
		this(counterRepository, new DailyAffiliateIdAllocator(counterRepository), membershipRepository, clock);
	}

	@Autowired
	public AffiliateIdService(
			DailyAffiliateIdCounterRepository counterRepository,
			DailyAffiliateIdAllocator counterAllocator,
			AffiliateMembershipRepository membershipRepository,
			Clock clock) {
		this.counterRepository = counterRepository;
		this.counterAllocator = counterAllocator;
		this.membershipRepository = membershipRepository;
		this.clock = clock == null ? Clock.systemUTC() : clock;
	}

	/**
	 * Generates a permanent, concurrency-safe Affiliate ID with format ZP-YYMMDD-SSS
	 * using Asia/Kolkata as the business activation timezone and supports serials beyond 999
	 * (e.g. ZP-261126-1000). Counter allocation runs in an isolated REQUIRES_NEW transaction
	 * synchronized inside the JVM with bounded retries for cross-node transient deadlocks.
	 */
	public String generateNextAffiliateId() {
		LocalDate businessDate = LocalDate.now(clock.withZone(BUSINESS_TIME_ZONE));
		int serial = allocateNextSerial(businessDate);
		String datePart = businessDate.format(DATE_FORMATTER);
		String serialPart = String.format("%03d", serial);
		return "ZP-" + datePart + "-" + serialPart;
	}

	private int allocateNextSerial(LocalDate businessDate) {
		RuntimeException lastFailure = null;
		for (int attempt = 0; attempt < MAX_LOCK_RETRIES; attempt++) {
			try {
				synchronized (counterLock) {
					return counterAllocator.allocateSerial(businessDate);
				}
			} catch (TransientDataAccessException ex) {
				lastFailure = ex;
			}
		}
		throw lastFailure != null
				? lastFailure
				: new IllegalStateException("Unable to allocate daily Affiliate ID serial.");
	}

	/**
	 * Generates a separate unique referral code (e.g., ZP9X2K7B) distinct from the public Affiliate ID.
	 */
	public String generateUniqueReferralCode() {
		for (int attempt = 0; attempt < 50; attempt++) {
			StringBuilder sb = new StringBuilder("ZP");
			for (int i = 0; i < 6; i++) {
				sb.append(REFERRAL_CHARS.charAt(RANDOM.nextInt(REFERRAL_CHARS.length())));
			}
			String candidate = sb.toString();
			if (!membershipRepository.existsByReferralCode(candidate)) {
				return candidate;
			}
		}
		return "ZP" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
	}
}
