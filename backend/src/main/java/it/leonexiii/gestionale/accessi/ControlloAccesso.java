package it.leonexiii.gestionale.accessi;

import java.util.List;

/**
 * Un caso in cui il tornello dà rosso. Ogni controllo è indipendente: per aggiungere un caso basta una nuova classe
 * {@code @Component} che implementa questa interfaccia e una riga in {@code regola_accesso} (con una migrazione).
 */
interface ControlloAccesso {

    /** Il codice della regola in {@code regola_accesso} (es. «CERTIFICATO»). */
    String regola();

    /** Nome breve per la pagina delle regole. */
    String nome();

    String descrizione();

    /** Ordine di valutazione e di presentazione dei motivi: prima il braccialetto, poi la persona, poi il momento. */
    int ordine();

    /** Si controlla quando si entra (di base sì). */
    default boolean valeInEntrata() {
        return true;
    }

    /** Si controlla quando si esce, dal lettore sul lato d'uscita (di base no). */
    default boolean valeInUscita() {
        return false;
    }

    /** Vale anche per chi ha il golden pass (es. braccialetto smarrito o ban)? */
    default boolean valeConGoldenPass() {
        return false;
    }

    /** I parametri modificabili, con il valore predefinito. */
    default List<Parametro> parametri() {
        return List.of();
    }

    /** I motivi del rosso (o gli avvisi) per questo passaggio; vuoto se è tutto a posto. */
    List<Motivo> verifica(Contesto c, Parametri p);

    /** Un parametro numerico di una regola; {@code facoltativo} vuol dire che può restare vuoto. */
    record Parametro(String nome, String etichetta, String unita, Integer predefinito, boolean facoltativo, String seVuoto, int max) {}
}
