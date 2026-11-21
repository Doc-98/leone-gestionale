/** Il nome della società in cui si lavora: lo scrive `App.svelte` appena lo sa, lo legge la barra di pagina sul telefono. */
class Ambiente {
  societa = $state('');
}

export const ambiente = new Ambiente();
