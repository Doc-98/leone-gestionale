package it.leonexiii.gestionale.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** I lavori a orario (es. i promemoria email del mattino). */
@Configuration
@EnableScheduling
class Pianificazioni {}
