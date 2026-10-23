package it.leonexiii.gestionale.identita;

/**
 * Cosa può fare un ruolo (ADR 0010). I ruoli sono dati, modificabili dagli admin; i permessi sono fissi perché
 * ognuno corrisponde a controlli nel codice. Chiunque abbia un ruolo vede i propri turni, il piano vasca, il
 * tornello, la lista e le presenze dei propri turni: quelle sono le basi, non permessi.
 */
public enum Permesso {
    TURNI("Turni e staff", "Fa i turni", "Compare nella pianificazione e può accettare le sostituzioni", false),
    PIANIFICAZIONE("Turni e staff", "Pianificazione", "Turni di tutti, griglia, settimana tipo, generazione del mese", true),
    APPROVAZIONI("Turni e staff", "Approvazioni", "Approvare cambi orario, sostituzioni e scambi", true),
    ORE_STAFF("Turni e staff", "Ore dello staff", "Ore, compensi e fogli ore di tutto lo staff", true),
    ANAGRAFICHE_STAFF("Turni e staff", "Mansioni e tariffe", "Mansioni, postazioni e tariffe orarie", true),
    PIANO_MODIFICA("Piscina", "Modifica del piano", "Corsi, turni dei corsi, eventi, chiusure e fasce", true),
    ISCRIZIONI("Piscina", "Iscrizioni", "Iscrivere e ritirare dai turni dei corsi, vedere tutte le liste", true),
    PRESENZE("Piscina", "Presenze di tutti", "Segnare le presenze di tutti i turni, non solo dei propri", true),
    CLIENTI("Clienti e accessi", "Clienti", "Scheda cliente: certificati, tesseramenti, abbonamenti", true),
    BRACCIALETTI("Clienti e accessi", "Braccialetti e ban", "Assegnare braccialetti, segnarli smarriti, bannare", true),
    GOLDEN_PASS("Clienti e accessi", "Golden pass", "Dare e togliere il golden pass", true),
    SCONTI("Clienti e accessi", "Cambiare l'importo proposto", "Scontare o cambiare l'importo proposto dal listino, scrivendo il motivo", true),
    REGOLE_TORNELLO("Sistema", "Regole del tornello", "Accendere, spegnere e configurare i controlli del tornello", true),
    RUOLI("Sistema", "Ruoli e permessi", "Creare ruoli, cambiarne i permessi e assegnarli alle persone", true),
    ACCOUNT("Sistema", "Account", "Creare gli account dello staff, disattivarli, reimpostare le password", true),
    IMPOSTAZIONI("Sistema", "Impostazioni della società", "Dati della società, stagioni e quota associativa, gruppi di clienti, listino e regole di prezzo", true);

    private final String gruppo;
    private final String nome;
    private final String descrizione;
    private final boolean potere;

    Permesso(String gruppo, String nome, String descrizione, boolean potere) {
        this.gruppo = gruppo;
        this.nome = nome;
        this.descrizione = descrizione;
        this.potere = potere;
    }

    public String gruppo() { return gruppo; }
    public String nome() { return nome; }
    public String descrizione() { return descrizione; }

    /** Un potere ce l'ha sempre anche ADMIN; «Fa i turni» invece è una caratteristica, non un potere. */
    public boolean potere() { return potere; }
}
