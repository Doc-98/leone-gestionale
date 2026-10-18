package it.leonexiii.gestionale.identita;

import java.util.UUID;

/** Id e nome completo: quanto basta agli altri moduli per mostrare una persona. */
public record PersonaBreve(UUID id, String nome) {}
