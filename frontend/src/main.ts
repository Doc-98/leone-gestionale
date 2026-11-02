import { mount } from 'svelte';
import '@fontsource/ibm-plex-sans/latin-400.css';
import '@fontsource/ibm-plex-sans/latin-500.css';
import '@fontsource/ibm-plex-sans/latin-600.css';
import '@fontsource/ibm-plex-sans/latin-700.css';
import '@fontsource/ibm-plex-mono/latin-500.css';
import '@fontsource/bricolage-grotesque/latin-800.css';
import './app.css';
// il tema scelto in Impostazioni › Aspetto: segue anche il dispositivo quando cambia
import './lib/tema.svelte';
import App from './App.svelte';

export default mount(App, { target: document.getElementById('app')! });
