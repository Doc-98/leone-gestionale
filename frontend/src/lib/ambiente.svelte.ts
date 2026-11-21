/**
 * Lo stato che `App.svelte` condivide con le pagine: il nome della società (lo scrive `App` appena lo sa, lo legge la barra di
 * pagina sul telefono) e il menu «Altro» del telefono, che si apre anche toccando l'account nella barra di pagina.
 */
class Ambiente {
  societa = $state('');
  menuAltro = $state(false);
}

export const ambiente = new Ambiente();
