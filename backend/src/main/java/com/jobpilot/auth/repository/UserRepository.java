package com.jobpilot.auth.repository;

import com.jobpilot.auth.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Los emails se buscan ya normalizados a minúsculas (ver {@code AuthService}). */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
