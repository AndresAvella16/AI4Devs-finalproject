package com.jobpilot.profile.repository;

import com.jobpilot.profile.domain.Profile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Perfiles. Cada usuario tiene como mucho uno ({@code uq_profiles_user_id}). */
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUserId(UUID userId);
}
