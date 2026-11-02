# 0025 · Crediti di phon e docce sul braccialetto, con le macchine non collegate

**Stato:** accettata (il formato vero è ancora da conoscere)

## Contesto

L'ADR 0018 (parte 9) metteva il credito sul server: le macchine chiedono al gestionale e lui scala i crediti. Alla Leone
però, a quanto sappiamo ora (novembre 2026), la segreteria ricarica il braccialetto dal gestionale attuale, che registra
il pagamento e **scrive il nuovo totale nella memoria del braccialetto**; le centraline di phon e docce leggono e scalano
i crediti lì e **non sembrano collegate alla rete**. Il gestionale quindi non sa quanti crediti restano su un braccialetto
dopo che è uscito dalla segreteria. Non sappiamo ancora se le macchine non si possono collegare o se semplicemente non lo
sono, né come sono scritti e cifrati i dati sul braccialetto: è la strada b) dell'ADR 0018.

Il gestionale resta generico: altre società hanno macchine collegate.

## Decisione

- **Un'impostazione, «Dove sta il credito»** (`regole_borsellino.crediti_su`, V25): `SERVER` (macchine collegate, come
  nell'ADR 0018) o `BRACCIALETTO` (macchine non collegate). Con `BRACCIALETTO` la ricarica «dalla scheda» non c'è più:
  si ricarica solo leggendo e scrivendo il braccialetto.
- **Il formato è un pezzo sostituibile** (`servizi.FormatoBraccialetto`): dice cosa il lettore deve leggere (tipo di
  chip, chiavi dei settori, blocchi), quanti crediti ci sono in una memoria e quali blocchi scrivere per un saldo nuovo.
  Se ne usa uno, scelto sul server con `gestionale.braccialetti.formato` (variabile `BRACCIALETTI_FORMATO`):
  `NON_NOTO` (predefinito: non legge e non scrive, lo dice) e `PROVA` (inventato, per la demo e i test: blocco 4 con
  «LG01», i crediti e i crediti invertiti, chiave di fabbrica). Quando avremo formato e crittografia veri si aggiunge
  un'implementazione: il resto non cambia. **Le chiavi vanno solo nelle variabili d'ambiente del server**, mai nel
  repository; il server le dà al PC che legge solo a chi ha il permesso «Borsellino e ricariche».
- **Il PC legge e scrive byte, il server li interpreta.** Il lettore (`frontend/src/lib/chip.svelte.ts`) legge i blocchi
  richiesti e scrive quelli che il server gli manda. Oggi c'è solo il **lettore simulato**, con il formato di prova:
  tiene la memoria dei braccialetti nel browser. Il lettore vero (PC/SC nel programma per Windows) si aggiunge quando
  sapremo che chip sono; la Web NFC del telefono legge solo il seriale, non questa memoria.
- **Ricarica in due tempi** (`scrittura_braccialetto`): 1) con la memoria appena letta il server controlla l'importo
  (minimo, multiplo del credito), calcola i blocchi del saldo nuovo e segna la ricarica come *preparata*; 2) il PC scrive,
  rilegge e manda la memoria riletta: se dice il saldo nuovo il server registra addebito, pagamento e crediti
  (*confermata*), altrimenti niente. Una ricarica preparata e mai confermata resta **in sospeso** e si vede nella scheda:
  si rilegge e si registra, o si annulla; non si annulla se il braccialetto riletto ha già i crediti nuovi. Con una
  ricarica in sospeso non se ne prepara un'altra e le letture non toccano il registro.
- **L'ultimo saldo visto** (`saldo_braccialetto`): scritto alla ricarica, letto al banco dal lettore o **scritto a mano
  dal display della macchina** (le centraline mostrano i crediti rimasti). È un massimo: i crediti veri possono essere di
  meno. A ogni lettura di un braccialetto attivo la differenza va nel registro della persona: in meno sono «consumati alle
  macchine», in più «trovati sul braccialetto»; alla prima lettura il registro si allinea e basta.
- **Riconsegna**: prima di rimborsare i crediti si legge il braccialetto (o si scrive il saldo dal display); se non si
  riesce, si può scegliere di rimborsare l'ultimo saldo visto, sapendo che può essere più alto del vero. Dopo la
  riconsegna il braccialetto si **azzera** (`/azzeramento`), perché chi lo riceverà non trovi i crediti di un altro.
- **Le macchine simulate** non registrano niente, come quelle vere: leggono la memoria, scalano i crediti del servizio e
  riscrivono (non contano gli usi gratuiti, che le centraline vere gestiscono da sé).

## Conseguenze

- Con `BRACCIALETTO` il registro dei crediti di una persona è «quanto risultava l'ultima volta», e i consumi compaiono
  solo quando qualcuno legge il braccialetto. Gli utilizzi per macchina (pagina Phon e docce) non arrivano più.
- Braccialetto smarrito: si perde quello che c'era sopra; il registro conosce solo l'ultimo saldo visto (regola
  «crediti persi» invariata).
- Per usare le macchine vere servono: il tipo di chip, le chiavi dei settori, il formato dei crediti (e come sono cifrati),
  e un lettore da scrivania che scriva (PC/SC). Con questi si scrivono un nuovo `FormatoBraccialetto` e il lettore del PC.
- Se un giorno le macchine si collegano, basta tornare a `SERVER`: chiavi dei dispositivi e API dell'ADR 0018 ci sono già.
