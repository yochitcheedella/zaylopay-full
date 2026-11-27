package com.zaylope.registration.affiliate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_affiliate_id_counters")
public class DailyAffiliateIdCounter {

	@Id
	@Column(name = "counter_date", nullable = false)
	private LocalDate counterDate;

	@Column(name = "last_serial", nullable = false)
	private int lastSerial = 0;

	@Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant updatedAt;

	protected DailyAffiliateIdCounter() {
	}

	public DailyAffiliateIdCounter(LocalDate counterDate, int initialSerial) {
		this.counterDate = counterDate;
		this.lastSerial = initialSerial;
		this.updatedAt = Instant.now();
	}

	@PrePersist
	@PreUpdate
	void updateTimestamp() {
		this.updatedAt = Instant.now();
	}

	public LocalDate getCounterDate() {
		return counterDate;
	}

	public int getLastSerial() {
		return lastSerial;
	}

	public int incrementSerial() {
		this.lastSerial++;
		this.updatedAt = Instant.now();
		return this.lastSerial;
	}
}
