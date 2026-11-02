package it.leonexiii.gestionale.servizi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Sceglie il formato dei crediti sul braccialetto dalla proprietà {@code gestionale.braccialetti.formato} (ADR 0025). */
@Configuration
class FormatiBraccialetto {

    @Bean
    FormatoBraccialetto formatoBraccialetto(@Value("${gestionale.braccialetti.formato:NON_NOTO}") String formato) {
        return switch (formato.strip().toUpperCase()) {
            case "PROVA" -> new FormatoProva();
            case "NON_NOTO", "" -> new FormatoNonNoto();
            default -> throw new IllegalStateException("Formato dei braccialetti sconosciuto: " + formato + " (NON_NOTO o PROVA).");
        };
    }
}
