package com.jobpilot.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj de la aplicación. Todo cálculo de "hoy" se hace con este bean (nunca {@code LocalDate.now()} directo). */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
