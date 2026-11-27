package com.zaylope.registration.affiliate;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyAffiliateIdCounterRepository extends JpaRepository<DailyAffiliateIdCounter, LocalDate> {

	@Modifying
	@Query(
			value = "INSERT INTO daily_affiliate_id_counters (counter_date, last_serial, updated_at) "
					+ "VALUES (:counterDate, 0, CURRENT_TIMESTAMP(6)) "
					+ "ON DUPLICATE KEY UPDATE counter_date = counter_date",
			nativeQuery = true)
	void initializeCounterIfAbsent(@Param("counterDate") LocalDate counterDate);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM DailyAffiliateIdCounter c WHERE c.counterDate = :counterDate")
	Optional<DailyAffiliateIdCounter> findByCounterDateWithLock(@Param("counterDate") LocalDate counterDate);
}
