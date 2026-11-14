import { mount } from 'svelte';
import '@fontsource/ibm-plex-sans/latin-400.css';
import '@fontsource/ibm-plex-sans/latin-500.css';
import '@fontsource/ibm-plex-sans/latin-600.css';
import '@fontsource/ibm-plex-sans/latin-700.css';
import '@fontsource/ibm-plex-mono/latin-500.css';
import '@fontsource/bricolage-grotesque/latin-800.css';
import '../app.css';
import Vetrina from './Vetrina.svelte';

// La vetrina della demo (ADR 0027): sceglie quale app e quale dispositivo mostrare. Solo in sviluppo.
export default mount(Vetrina, { target: document.getElementById('vetrina')! });
