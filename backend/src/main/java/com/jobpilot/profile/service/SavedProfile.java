package com.jobpilot.profile.service;

/** Resultado de {@code PUT /profile}: {@code created} es true si el perfil no existía (→ 201) y false si se ha reemplazado (→ 200). */
public record SavedProfile(ProfileDetails details, boolean created) {
}
