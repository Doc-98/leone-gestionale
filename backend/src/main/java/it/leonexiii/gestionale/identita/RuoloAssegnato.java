package it.leonexiii.gestionale.identita;

import java.io.Serializable;
import java.util.UUID;

/** Ruolo con il suo ambito: impiantoId null = tutta la società. */
public record RuoloAssegnato(Ruolo ruolo, UUID impiantoId) implements Serializable {}
