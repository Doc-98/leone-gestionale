package it.leonexiii.gestionale.accessi;

import java.util.Map;

/** I parametri di una regola, letti dalla tabella; se mancano valgono quelli predefiniti del controllo. */
record Parametri(Map<String, Object> valori) {

    Integer intero(String nome) {
        Object v = valori.get(nome);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return Integer.valueOf(v.toString().strip());
    }

    int intero(String nome, int predefinito) {
        Integer v = intero(nome);
        return v == null ? predefinito : v;
    }
}
