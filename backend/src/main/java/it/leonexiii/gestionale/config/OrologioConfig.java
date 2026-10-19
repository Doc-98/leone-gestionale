package it.leonexiii.gestionale.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** L'ora «di calendario» della società: Europe/Rome. Un bean, così nei test si può fermare il tempo. */
@Configuration
class OrologioConfig {

    @Bean
    Clock orologio() {
        return Clock.system(ZoneId.of("Europe/Rome"));
    }
}
