package it.leonexiii.gestionale;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Un test dell'applicazione intera: server vero su una porta a caso, Postgres, dati demo, orologio e casella email di
 * prova. Tutti i test usano la stessa configurazione, così Spring avvia l'applicazione una volta sola per tutta la suite.
 * I lavori a orario (promemoria del giorno, scadenze del borsellino) non partono da soli: li fa partire il test che li prova.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"gestionale.promemoria.cron=-", "gestionale.borsellino.cron=-"})
@Import({TestcontainersConfiguration.class, AmbienteDiProva.class})
@ActiveProfiles("demo")
@interface TestGestionale {
}
