package it.leonexiii.gestionale.identita;

import java.io.Serializable;
import java.util.UUID;

/** Ruolo (codice, es. «COORDINATORE») con il suo ambito: impiantoId null = tutta la società. */
public record RuoloAssegnato(String ruolo, UUID impiantoId) implements Serializable {}
