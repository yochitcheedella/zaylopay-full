package com.zaylope.registration.registration;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicantRegistrationLockRepository
		extends JpaRepository<ApplicantRegistrationLock, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select lockRow from ApplicantRegistrationLock lockRow "
			+ "where lockRow.mobileNumber = :mobileNumber")
	Optional<ApplicantRegistrationLock> findByMobileNumberForUpdate(
			@Param("mobileNumber") String mobileNumber);
}
