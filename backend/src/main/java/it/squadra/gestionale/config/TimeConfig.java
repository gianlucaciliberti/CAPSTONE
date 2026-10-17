package it.squadra.gestionale.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Orologio unico dell'applicazione. Gli istanti sono salvati in UTC; il fuso della squadra
 * (Europe/Rome) si applica quando si ragiona su giorni e orari locali.
 * Iniettare {@link Clock} invece di chiamare {@code Instant.now()} rende il codice testabile.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(properties.timezone());
    }
}
