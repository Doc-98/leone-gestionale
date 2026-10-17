package it.leonexiii.gestionale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Leone Gestionale: un'unica applicazione divisa in moduli (un pacchetto per modulo).
 * <ul>
 *   <li>{@code identita} – persone, account, ruoli, login</li>
 *   <li>{@code struttura} – sedi, impianti, spazi, discipline</li>
 *   <li>{@code staff} – mansioni, postazioni, turni</li>
 * </ul>
 * Un modulo non legge le tabelle di un altro: usa le sue classi pubbliche o gli id.
 */
@SpringBootApplication
public class GestionaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(GestionaleApplication.class, args);
    }
}
