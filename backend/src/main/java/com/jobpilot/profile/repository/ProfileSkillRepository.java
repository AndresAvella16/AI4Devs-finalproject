package com.jobpilot.profile.repository;

import com.jobpilot.profile.domain.ProfileSkill;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileSkillRepository extends JpaRepository<ProfileSkill, UUID> {

    /** Habilidades del perfil por nombre ({@code profile_skills} no guarda un orden propio). */
    @Query("SELECT s FROM ProfileSkill s WHERE s.profile.id = :profileId ORDER BY lower(s.name)")
    List<ProfileSkill> findByProfileIdOrdered(@Param("profileId") UUID profileId);

    /** Borra las del perfil en una sola sentencia, antes de insertar las nuevas al reemplazarlo. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ProfileSkill s WHERE s.profile.id = :profileId")
    int deleteByProfileId(@Param("profileId") UUID profileId);
}
