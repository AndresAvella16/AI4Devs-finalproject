package com.jobpilot.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT de la API (sección 2.5): HS256 con un secreto de al menos 256 bits, emisor {@value #ISSUER} y fechas
 * validadas con el {@link Clock} de la aplicación. El mismo {@link JwtDecoder} lo usa el resource server de
 * Spring Security para validar el {@code Authorization: Bearer} de cada petición.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
public class JwtConfig {

    public static final String ISSUER = "jobpilot";

    /** 256 bits: el mínimo para HS256. */
    static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtSecretKey(SecurityProperties properties) {
        return secretKey(properties.jwt().secret());
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return encoder(jwtSecretKey);
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, Clock clock) {
        return decoder(jwtSecretKey, clock);
    }

    public static SecretKey secretKey(String secret) {
        int length = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET (jobpilot.security.jwt.secret) debe tener al menos "
                    + MIN_SECRET_BYTES + " bytes (256 bits); tiene " + length);
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public static JwtEncoder encoder(SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    /** Solo acepta HS256 con esta clave, con {@code exp} obligatorio, sin margen de reloj y del emisor {@value #ISSUER}. */
    public static JwtDecoder decoder(SecretKey key, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        timestamps.setAllowEmptyExpiryClaim(false);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
        return decoder;
    }
}
