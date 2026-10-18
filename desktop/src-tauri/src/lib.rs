//! Leone Gestionale per Windows.
//!
//! Il programma è una finestra sul server della società: all'avvio mostra una pagina inclusa nel programma
//! (`avvio/index.html`) che controlla il server salvato e poi apre l'interfaccia servita da lì. Così sessione e
//! cookie restano sullo stesso indirizzo del server, e l'interfaccia si aggiorna da sola come una PWA.
//!
//! La parte nativa espone pochi comandi, chiamabili solo dalla pagina di avvio e dal server configurato
//! (vedi ADR 0006). Lettore NFC e stampa arriveranno qui, dietro gli stessi comandi che il frontend
//! usa tramite `lib/dispositivi.ts`.

use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;
use tauri::{ipc::CapabilityBuilder, AppHandle, Manager, Url};

#[derive(Serialize, Deserialize, Default)]
struct Impostazioni {
    server: Option<String>,
}

fn file_impostazioni(app: &AppHandle) -> Result<PathBuf, String> {
    let dir = app.path().app_config_dir().map_err(|e| e.to_string())?;
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(dir.join("impostazioni.json"))
}

fn leggi(app: &AppHandle) -> Impostazioni {
    file_impostazioni(app)
        .ok()
        .and_then(|f| fs::read_to_string(f).ok())
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

/// «https://gestionale.leone.local/qualcosa» → «https://gestionale.leone.local»; solo http e https.
fn origine(indirizzo: &str) -> Result<Url, String> {
    let mut url = Url::parse(indirizzo.trim()).map_err(|_| "Indirizzo non valido: es. https://gestionale.leone.local".to_string())?;
    if url.scheme() != "https" && url.scheme() != "http" {
        return Err("L'indirizzo deve iniziare con https:// (o http:// in rete interna).".into());
    }
    if url.host_str().is_none() {
        return Err("Manca il nome del server.".into());
    }
    url.set_path("/");
    url.set_query(None);
    url.set_fragment(None);
    Ok(url)
}

/// Il server configurato può chiamare i comandi del programma (e solo lui, oltre alla pagina di avvio).
fn autorizza(app: &AppHandle, server: &Url) -> Result<(), String> {
    let modello = format!("{}*", server.as_str());
    // Un nome per indirizzo: se si cambia server senza riavviare, il permesso nuovo si aggiunge all'altro
    let nome: String = server.as_str().chars().map(|c| if c.is_ascii_alphanumeric() { c } else { '-' }).collect();
    app.add_capability(
        CapabilityBuilder::new(format!("server-{nome}"))
            .remote(modello)
            .window("principale")
            .permission("core:default")
            .permission("allow-informazioni")
            .permission("allow-torna-all-avvio"),
    )
    .map_err(|e| e.to_string())
}

#[tauri::command]
fn server(app: AppHandle) -> Option<String> {
    leggi(&app).server
}

/// Salva l'indirizzo del server e lo autorizza; la pagina di avvio poi ci naviga.
#[tauri::command]
fn imposta_server(app: AppHandle, indirizzo: String) -> Result<String, String> {
    let url = origine(&indirizzo)?;
    let imp = Impostazioni { server: Some(url.to_string()) };
    let testo = serde_json::to_string_pretty(&imp).map_err(|e| e.to_string())?;
    fs::write(file_impostazioni(&app)?, testo).map_err(|e| e.to_string())?;
    autorizza(&app, &url)?;
    Ok(url.to_string())
}

/// Torna alla pagina di avvio (per cambiare server o riprovare la connessione).
#[tauri::command]
fn torna_all_avvio(app: AppHandle) -> Result<(), String> {
    let finestra = app.get_webview_window("principale").ok_or("Finestra non trovata")?;
    let avvio: Url = if cfg!(windows) { "http://tauri.localhost/?cambia=1" } else { "tauri://localhost/?cambia=1" }
        .parse()
        .map_err(|e: url::ParseError| e.to_string())?;
    finestra.navigate(avvio).map_err(|e| e.to_string())
}

/// Versione e sistema, mostrati nell'interfaccia («Programma per Windows 0.1.0»).
#[tauri::command]
fn informazioni(app: AppHandle) -> serde_json::Value {
    serde_json::json!({
        "versione": app.package_info().version.to_string(),
        "sistema": std::env::consts::OS,
    })
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .setup(|app| {
            if let Some(s) = leggi(app.handle()).server {
                if let Ok(url) = origine(&s) {
                    autorizza(app.handle(), &url)?;
                }
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![server, imposta_server, torna_all_avvio, informazioni])
        .run(tauri::generate_context!())
        .expect("errore all'avvio di Leone Gestionale");
}
