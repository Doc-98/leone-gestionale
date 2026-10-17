# 0001 · PostgreSQL, database relazionale

**Contesto.** I dati del gestionale sono una rete di relazioni: per decidere se un bracciale può entrare
servono credenziale → persona → iscrizioni → corso → lezioni → certificato → pagamenti. Scambi di turni e
pagamenti devono essere atomici. L'admin vorrà riepiloghi non previsti in anticipo.

**Decisione.** PostgreSQL. Vincoli (chiavi esterne, unicità, controlli) nel database; transazioni per le
operazioni composte; colonne JSONB per le parti poco strutturate. Schema versionato con Flyway.

**Conseguenze.** Le query di riepilogo sono semplici; i dati restano coerenti anche con bug applicativi.
Lo schema va fatto evolvere con migrazioni, mai a mano. Un database a documenti è stato scartato:
avrebbe richiesto dati duplicati o collegamenti fatti a mano nel codice.
