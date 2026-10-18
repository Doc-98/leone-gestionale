fn main() {
    // I comandi del programma diventano permessi (allow-server, allow-informazioni, …):
    // le capability decidono chi può chiamarli (pagina di avvio, server configurato).
    tauri_build::try_build(tauri_build::Attributes::new().app_manifest(
        tauri_build::AppManifest::new().commands(&["server", "imposta_server", "torna_all_avvio", "informazioni"]),
    ))
    .expect("errore nella preparazione del programma");
}
