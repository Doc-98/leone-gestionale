# 0002 · Backend Java 21 + Spring Boot, monolite modulare

**Contesto.** Backend scritto da noi (niente BaaS), dominio gestionale con ruoli, transazioni e regole.
Alternativa valutata: Python + FastAPI (più leggero, meno struttura).

**Decisione.** Java 21 + Spring Boot 4: Spring Security, Spring Data JPA, Flyway, Spring Modulith,
springdoc-openapi, Testcontainers. Un'unica applicazione divisa in moduli (`identita`, `struttura`,
`staff`, `pianovasca`, `clienti`, `accessi`) con confini verificati da un test.

**Conseguenze.** Più codice di contorno rispetto a FastAPI, in cambio di struttura e strumenti maturi.
I moduli comunicano tramite classi pubbliche o id, mai leggendo le tabelle altrui: un modulo si potrà
separare in futuro senza riscriverlo.
