// Su Windows, nessuna finestra di console accanto al programma
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    leone_gestionale_lib::run()
}
