package com.zaylope.registration.admin;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

	Optional<AdminUser> findByUsernameIgnoreCase(String username);

	Optional<AdminUser> findByEmailIgnoreCase(String email);

	boolean existsByUsernameIgnoreCase(String username);
}
