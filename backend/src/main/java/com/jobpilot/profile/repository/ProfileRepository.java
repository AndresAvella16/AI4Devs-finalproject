package com.jobpilot.profile.repository;

import com.jobpilot.profile.domain.Profile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Perfiles (JP-BE-06 solo lo lee; JP-BE-04 añadirá la escritura). Cada usuario tiene como mucho uno. */
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUserId(UUID userId);
}
