use std::sync::Arc;
use std::time::Duration;
use tauri::menu::{MenuBuilder, MenuItemBuilder};
use tauri::tray::TrayIconBuilder;
use tauri::Manager;
use tokio::sync::Mutex;

#[cfg(target_os = "windows")]
use std::os::windows::process::CommandExt;

const TRON_PORT: u16 = 8000;
const BACKEND_RETRY_WINDOW: Duration = Duration::from_secs(180);
const BACKEND_RETRY_DELAY: Duration = Duration::from_secs(5);

// ============================================================================
// Backend Manager - starts and manages the Java backend process
// ============================================================================

struct ChildHandle {
    child: tokio::process::Child,
}

impl ChildHandle {
    async fn kill(&mut self) {
        let _ = self.child.kill().await;
    }
}

struct BackendManager {
    backend: Option<ChildHandle>,
    stderr_tail: Arc<Mutex<Vec<u8>>>,
    boot_state: BackendBootState,
    boot_error: Option<String>,
    boot_task_running: bool,
    sidecar_staged: bool,
}

impl Default for BackendManager {
    fn default() -> Self {
        Self {
            backend: None,
            stderr_tail: Arc::new(Mutex::new(Vec::new())),
            boot_state: BackendBootState::Starting,
            boot_error: None,
            boot_task_running: false,
            sidecar_staged: false,
        }
    }
}

impl BackendManager {
    async fn stop_all(&mut self) {
        if let Some(ref mut h) = self.backend {
            h.kill().await;
        }
        self.backend = None;
    }

    fn is_backend_running(&mut self) -> bool {
        if let Some(ref mut h) = self.backend {
            match h.child.try_wait() {
                Ok(None) => return true,
                Ok(Some(_)) | Err(_) => {
                    self.backend = None;
                    return false;
                }
            }
        }
        false
    }

    fn check_backend_exit_status(&mut self) -> Option<String> {
        if let Some(ref mut h) = self.backend {
            match h.child.try_wait() {
                Ok(None) => None,
                Ok(Some(status)) => {
                    self.backend = None;
                    Some(format!("Backend process exited with status: {}", status))
                }
                Err(e) => {
                    self.backend = None;
                    Some(format!("Failed to query backend process status: {}", e))
                }
            }
        } else {
            None
        }
    }
}

type SharedBackend = Arc<Mutex<BackendManager>>;

#[derive(Clone, Copy, serde::Serialize)]
#[serde(rename_all = "snake_case")]
enum BackendBootState {
    Starting,
    Ready,
    Failed,
}

#[derive(serde::Serialize)]
struct BackendBootStatus {
    state: BackendBootState,
    message: Option<String>,
}

// ============================================================================
// Helpers
// ============================================================================

fn home_dir() -> String {
    std::env::var("HOME")
        .or_else(|_| std::env::var("USERPROFILE"))
        .unwrap_or_default()
}

fn launcher_log_path() -> std::path::PathBuf {
    #[cfg(target_os = "windows")]
    {
        if let Ok(local) = std::env::var("LOCALAPPDATA") {
            return std::path::PathBuf::from(local)
                .join("OpenTron")
                .join("logs")
                .join("desktop-backend.log");
        }
    }

    std::path::PathBuf::from(home_dir())
        .join(".opentron")
        .join("logs")
        .join("desktop-backend.log")
}

fn backend_log_file_path() -> std::path::PathBuf {
    #[cfg(target_os = "windows")]
    {
        if let Ok(local) = std::env::var("LOCALAPPDATA") {
            return std::path::PathBuf::from(local)
                .join("OpenTron")
                .join("logs")
                .join("opentron-backend.log");
        }
    }

    std::path::PathBuf::from(home_dir())
        .join(".opentron")
        .join("logs")
        .join("opentron-backend.log")
}

fn backend_working_dir() -> std::path::PathBuf {
    #[cfg(target_os = "windows")]
    {
        if let Ok(local) = std::env::var("LOCALAPPDATA") {
            return std::path::PathBuf::from(local).join("OpenTron");
        }
    }

    std::path::PathBuf::from(home_dir()).join(".opentron")
}

fn append_launcher_log(message: &str) {
    use std::io::Write;

    let path = launcher_log_path();
    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }

    let ts = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);

    if let Ok(mut file) = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
    {
        let _ = writeln!(file, "[{}] {}", ts, message);
    }
}

fn append_bytes_to_file(path: &std::path::Path, bytes: &[u8]) {
    use std::io::Write;

    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }

    if let Ok(mut file) = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
    {
        let _ = file.write_all(bytes);
    }
}

fn bundled_sidecar_dir_candidates() -> Vec<std::path::PathBuf> {
    let mut dirs = Vec::new();

    if let Ok(exe) = std::env::current_exe() {
        if let Some(exe_dir) = exe.parent() {
            // Typical Windows/Linux Tauri bundle location.
            dirs.push(exe_dir.join("resources").join("sidecar"));
            // Dev/local fallback when running unpackaged.
            dirs.push(exe_dir.join("sidecar"));
            // macOS app bundle location (Contents/Resources/sidecar).
            dirs.push(exe_dir.join("..").join("Resources").join("sidecar"));
        }
    }

    dirs
}

fn local_sidecar_dir() -> Option<std::path::PathBuf> {
    #[cfg(target_os = "windows")]
    {
        std::env::var("LOCALAPPDATA")
            .ok()
            .map(|p| std::path::PathBuf::from(p).join("OpenTron").join("sidecar"))
    }

    #[cfg(not(target_os = "windows"))]
    {
        None
    }
}

fn legacy_sidecar_dir() -> Option<std::path::PathBuf> {
    let home = home_dir();
    if home.is_empty() {
        None
    } else {
        Some(
            std::path::PathBuf::from(home)
                .join(".OpenTron")
                .join("sidecar"),
        )
    }
}

fn copy_dir_recursive(src: &std::path::Path, dst: &std::path::Path) -> std::io::Result<()> {
    std::fs::create_dir_all(dst)?;
    for entry in std::fs::read_dir(src)? {
        let entry = entry?;
        let src_path = entry.path();
        let dst_path = dst.join(entry.file_name());
        if src_path.is_dir() {
            copy_dir_recursive(&src_path, &dst_path)?;
        } else {
            if let Some(parent) = dst_path.parent() {
                std::fs::create_dir_all(parent)?;
            }
            std::fs::copy(&src_path, &dst_path)?;
        }
    }
    Ok(())
}

fn ensure_windows_sidecar_in_local_appdata() -> Result<(), String> {
    #[cfg(target_os = "windows")]
    {
        let Some(dst_dir) = local_sidecar_dir() else {
            append_launcher_log("LOCALAPPDATA is missing; cannot stage sidecar");
            return Err("LOCALAPPDATA is not set; cannot stage sidecar".to_string());
        };

        let src_dir = bundled_sidecar_dir_candidates()
            .into_iter()
            .find(|p| {
                p.join("backend.jar").exists()
                    && (p.join("jre").join("bin").join("javaw.exe").exists()
                        || p.join("jre").join("bin").join("java.exe").exists())
            })
            .ok_or_else(|| "Bundled sidecar not found in application resources".to_string())?;
        append_launcher_log(&format!(
            "Staging sidecar from {} to {}",
            src_dir.display(),
            dst_dir.display()
        ));

        std::fs::create_dir_all(&dst_dir)
            .map_err(|e| format!("Failed to create LocalAppData sidecar dir: {}", e))?;

        let dst_jar = dst_dir.join("backend.jar");
        std::fs::copy(src_dir.join("backend.jar"), &dst_jar)
            .map_err(|e| format!("Failed to copy backend.jar to LocalAppData: {}", e))?;

        let src_jre = src_dir.join("jre");
        let dst_jre = dst_dir.join("jre");
        if dst_jre.exists() {
            std::fs::remove_dir_all(&dst_jre)
                .map_err(|e| format!("Failed to clean previous LocalAppData JRE: {}", e))?;
        }
        copy_dir_recursive(&src_jre, &dst_jre)
            .map_err(|e| format!("Failed to copy JRE to LocalAppData: {}", e))?;

        if !dst_jar.exists()
            || (!dst_jre.join("bin").join("javaw.exe").exists()
                && !dst_jre.join("bin").join("java.exe").exists())
        {
            append_launcher_log("LocalAppData sidecar validation failed after copy");
            return Err("LocalAppData sidecar validation failed after copy".to_string());
        }

        append_launcher_log("Sidecar staged successfully in LocalAppData");

        Ok(())
    }

    #[cfg(not(target_os = "windows"))]
    {
        Ok(())
    }
}

fn sidecar_dir_candidates() -> Vec<std::path::PathBuf> {
    let mut dirs = Vec::new();
    if let Some(local) = local_sidecar_dir() {
        dirs.push(local);
    }
    dirs.extend(bundled_sidecar_dir_candidates());
    if let Some(legacy) = legacy_sidecar_dir() {
        dirs.push(legacy);
    }
    dirs
}

fn first_existing_file<F>(build_path: F) -> Option<std::path::PathBuf>
where
    F: Fn(&std::path::PathBuf) -> std::path::PathBuf,
{
    for dir in sidecar_dir_candidates() {
        let path = build_path(&dir);
        if path.exists() {
            return Some(path);
        }
    }
    None
}

fn backend_jar_path() -> std::path::PathBuf {
    first_existing_file(|dir| dir.join("backend.jar")).unwrap_or_else(|| {
        std::path::PathBuf::from(home_dir())
            .join(".OpenTron")
            .join("sidecar")
            .join("backend.jar")
    })
}

fn jre_java_path() -> std::path::PathBuf {
    #[cfg(target_os = "windows")]
    let path = first_existing_file(|dir| dir.join("jre").join("bin").join("java.exe"))
        .or_else(|| first_existing_file(|dir| dir.join("jre").join("bin").join("javaw.exe")));

    #[cfg(not(target_os = "windows"))]
    let path = first_existing_file(|dir| dir.join("jre").join("bin").join("java"));

    path.unwrap_or_else(|| {
        let home = home_dir();
        #[cfg(target_os = "windows")]
        {
            let java = std::path::PathBuf::from(&home)
                .join(".OpenTron")
                .join("sidecar")
                .join("jre")
                .join("bin")
                .join("java.exe");

            if java.exists() {
                java
            } else {
                std::path::PathBuf::from(home)
                    .join(".OpenTron")
                    .join("sidecar")
                    .join("jre")
                    .join("bin")
                    .join("javaw.exe")
            }
        }
        #[cfg(not(target_os = "windows"))]
        {
            std::path::PathBuf::from(home)
                .join(".OpenTron")
                .join("sidecar")
                .join("jre")
                .join("bin")
                .join("java")
        }
    })
}

async fn wait_for_url(url: &str, timeout: Duration) -> bool {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(2))
        .build()
        .unwrap();
    let deadline = tokio::time::Instant::now() + timeout;
    while tokio::time::Instant::now() < deadline {
        if let Ok(resp) = client.get(url).send().await {
            if resp.status().is_success() {
                return true;
            }
        }
        tokio::time::sleep(Duration::from_millis(500)).await;
    }
    false
}

fn spawn_stderr_drainer(
    stderr: tokio::process::ChildStderr,
    tail: Arc<Mutex<Vec<u8>>>,
    backend_log_path: std::path::PathBuf,
) {
    use tokio::io::AsyncReadExt;
    tokio::spawn(async move {
        let mut reader = stderr;
        let mut buf = vec![0u8; 4096];
        loop {
            match reader.read(&mut buf).await {
                Ok(0) => break,
                Err(_) => break,
                Ok(n) => {
                    append_bytes_to_file(&backend_log_path, &buf[..n]);
                    let mut t = tail.lock().await;
                    t.extend_from_slice(&buf[..n]);
                    if t.len() > 16 * 1024 {
                        let drop_n = t.len() - 16 * 1024;
                        t.drain(..drop_n);
                    }
                }
            }
        }
    });
}

fn spawn_stdout_drainer(
    stdout: tokio::process::ChildStdout,
    backend_log_path: std::path::PathBuf,
) {
    use tokio::io::AsyncReadExt;
    tokio::spawn(async move {
        let mut reader = stdout;
        let mut buf = vec![0u8; 4096];
        loop {
            match reader.read(&mut buf).await {
                Ok(0) => break,
                Err(_) => break,
                Ok(n) => {
                    append_bytes_to_file(&backend_log_path, &buf[..n]);
                }
            }
        }
    });
}

// ============================================================================
// Boot Sequence
// ============================================================================

async fn boot_backend_once(backend: SharedBackend) -> Result<(), String> {
    let should_stage = {
        let mgr = backend.lock().await;
        !mgr.sidecar_staged
    };

    if should_stage {
        ensure_windows_sidecar_in_local_appdata()?;
        let mut mgr = backend.lock().await;
        mgr.sidecar_staged = true;
    }

    let jar_path = backend_jar_path();
    let java_path = jre_java_path();
    let log_path = backend_log_file_path();
    let work_dir = backend_working_dir();

    let _ = std::fs::create_dir_all(&work_dir);
    if let Some(parent) = log_path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }

    append_launcher_log(&format!(
        "Backend spawn requested. java={} jar={} work_dir={} log_file={}",
        java_path.display(),
        jar_path.display(),
        work_dir.display(),
        log_path.display()
    ));

    if !jar_path.exists() {
        return Err(format!("Backend JAR not found: {}", jar_path.display()));
    }

    if !java_path.exists() {
        return Err(format!(
            "Java runtime not found: {}. Please reinstall the application.",
            java_path.display()
        ));
    }

    let mut cmd = tokio::process::Command::new(&java_path);
    cmd.arg("-Dspring.profiles.active=embedded")
        .arg(format!("-Dlogging.file.name={}", log_path.display()))
        .arg("-jar")
        .arg(&jar_path)
        .arg(format!("--server.port={}", TRON_PORT))
        .current_dir(&work_dir)
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped());

    #[cfg(target_os = "windows")]
    {
        // Ensure no console window flashes when backend retries on Windows.
        cmd.creation_flags(0x08000000);
    }

    let mut child = cmd
        .spawn()
        .map_err(|e| format!("Failed to start Java backend: {}", e))?;

    append_launcher_log("Backend process spawn succeeded");

    let stderr = child.stderr.take();
    let stdout = child.stdout.take();
    let mut mgr = backend.lock().await;
    let tail = mgr.stderr_tail.clone();
    mgr.backend = Some(ChildHandle { child });
    drop(mgr);

    if let Some(stderr) = stderr {
        spawn_stderr_drainer(stderr, tail, log_path.clone());
    }

    if let Some(stdout) = stdout {
        spawn_stdout_drainer(stdout, log_path);
    }

    Ok(())
}

async fn boot_backend_with_retries(backend: SharedBackend) -> Result<(), String> {
    {
        let mut mgr = backend.lock().await;
        if mgr.boot_task_running {
            return Ok(());
        }
        mgr.boot_task_running = true;
        mgr.boot_state = BackendBootState::Starting;
        mgr.boot_error = None;
    }

    append_launcher_log("Backend boot retry loop started");

    let deadline = tokio::time::Instant::now() + BACKEND_RETRY_WINDOW;
    let health_url = format!("http://127.0.0.1:{}/actuator/health", TRON_PORT);
    let mut last_error = String::new();
    let mut attempt: u32 = 0;

    let result = loop {
        if wait_for_url(&health_url, Duration::from_secs(2)).await {
            append_launcher_log("Backend health check succeeded");
            break Ok(());
        }

        let (should_spawn, exit_note, tail_ref) = {
            let mut mgr = backend.lock().await;
            let exit_note = mgr.check_backend_exit_status();
            let should_spawn = !mgr.is_backend_running();
            (should_spawn, exit_note, mgr.stderr_tail.clone())
        };

        if let Some(note) = exit_note {
            append_launcher_log(&note);
            let stderr_bytes = tail_ref.lock().await.clone();
            if !stderr_bytes.is_empty() {
                let stderr_tail = String::from_utf8_lossy(&stderr_bytes);
                append_launcher_log(&format!("Backend stderr tail: {}", stderr_tail));
            }
        }

        if should_spawn {
            attempt += 1;
            append_launcher_log(&format!("Backend spawn attempt {}", attempt));
            if let Err(e) = boot_backend_once(backend.clone()).await {
                append_launcher_log(&format!("Backend spawn attempt failed: {}", e));
                last_error = e;
            }
        }

        if tokio::time::Instant::now() >= deadline {
            if last_error.is_empty() {
                last_error =
                    "Backend failed to start within 3 minutes. Check logs for details.".to_string();
            }
            append_launcher_log(&format!("Backend boot failed after retries: {}", last_error));
            break Err(last_error.clone());
        }

        tokio::time::sleep(BACKEND_RETRY_DELAY).await;
    };

    let mut mgr = backend.lock().await;
    mgr.boot_task_running = false;

    match result {
        Ok(()) => {
            mgr.boot_state = BackendBootState::Ready;
            mgr.boot_error = None;
            append_launcher_log("Backend boot marked READY");
            Ok(())
        }
        Err(_) => {
            mgr.stop_all().await;
            mgr.boot_state = BackendBootState::Failed;
            mgr.boot_error = Some("Cannot connect to backend".to_string());
            append_launcher_log("Backend boot marked FAILED (Cannot connect to backend)");
            Err(last_error)
        }
    }
}

// ============================================================================
// Tauri Commands
// ============================================================================

#[tauri::command]
fn get_api_base() -> String {
    format!("http://127.0.0.1:{}", TRON_PORT)
}

#[tauri::command]
async fn start_backend(backend: tauri::State<'_, SharedBackend>) -> Result<(), String> {
    boot_backend_with_retries(backend.inner().clone()).await
}

#[tauri::command]
async fn stop_backend(backend: tauri::State<'_, SharedBackend>) -> Result<(), String> {
    backend.lock().await.stop_all().await;
    Ok(())
}

#[tauri::command]
async fn check_health() -> Result<bool, String> {
    let url = format!("http://127.0.0.1:{}/actuator/health", TRON_PORT);
    match reqwest::get(&url).await {
        Ok(resp) => Ok(resp.status().is_success()),
        Err(_) => Ok(false),
    }
}

#[tauri::command]
async fn get_backend_boot_status(
    backend: tauri::State<'_, SharedBackend>,
) -> Result<BackendBootStatus, String> {
    let mgr = backend.lock().await;
    Ok(BackendBootStatus {
        state: mgr.boot_state,
        message: mgr.boot_error.clone(),
    })
}

#[tauri::command]
async fn fetch_models(api_url: String) -> Result<Vec<serde_json::Value>, String> {
    let url = if api_url.is_empty() {
        format!("http://127.0.0.1:{}/v1/models", TRON_PORT)
    } else {
        format!("{}/v1/models", api_url)
    };

    match reqwest::get(&url).await {
        Ok(resp) => match resp.json::<serde_json::Value>().await {
            Ok(data) => {
                if let Some(models) = data.get("data").and_then(|d| d.as_array()) {
                    Ok(models.clone())
                } else {
                    Ok(vec![])
                }
            }
            Err(_) => Err("Failed to parse models response".to_string()),
        },
        Err(e) => Err(format!("Failed to fetch models: {}", e)),
    }
}

#[tauri::command]
async fn fetch_server_info(api_url: String) -> Result<serde_json::Value, String> {
    let url = if api_url.is_empty() {
        format!("http://127.0.0.1:{}/v1/info", TRON_PORT)
    } else {
        format!("{}/v1/info", api_url)
    };

    match reqwest::get(&url).await {
        Ok(resp) => resp
            .json::<serde_json::Value>()
            .await
            .map_err(|e| format!("Failed to parse response: {}", e)),
        Err(e) => Err(format!("Failed to fetch server info: {}", e)),
    }
}

#[tauri::command]
async fn fetch_savings(api_url: String) -> Result<serde_json::Value, String> {
    let url = if api_url.is_empty() {
        format!("http://127.0.0.1:{}/v1/savings", TRON_PORT)
    } else {
        format!("{}/v1/savings", api_url)
    };

    match reqwest::get(&url).await {
        Ok(resp) => resp
            .json::<serde_json::Value>()
            .await
            .map_err(|e| format!("Failed to parse response: {}", e)),
        Err(e) => Err(format!("Failed to fetch savings: {}", e)),
    }
}

#[tauri::command]
async fn fetch_agents(api_url: String) -> Result<serde_json::Value, String> {
    let url = if api_url.is_empty() {
        format!("http://127.0.0.1:{}/v1/agents", TRON_PORT)
    } else {
        format!("{}/v1/agents", api_url)
    };

    match reqwest::get(&url).await {
        Ok(resp) => resp
            .json::<serde_json::Value>()
            .await
            .map_err(|e| format!("Failed to parse response: {}", e)),
        Err(e) => Err(format!("Failed to fetch agents: {}", e)),
    }
}

#[tauri::command]
fn open_external_url(url: String) -> Result<(), String> {
    open::that(&url).map_err(|e| format!("Failed to open URL: {}", e))
}

// ============================================================================
// App Entry Point
// ============================================================================

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let backend: SharedBackend = Arc::new(Mutex::new(BackendManager::default()));
    let boot_backend_ref = backend.clone();

    tauri::Builder::default()
        .manage(backend.clone())
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_process::init())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.set_focus();
            }
        }))
        .setup(move |app| {
            // System tray
            let show = MenuItemBuilder::with_id("show", "Show / Hide").build(app)?;
            let health = MenuItemBuilder::with_id("health", "Health: starting...")
                .enabled(false)
                .build(app)?;
            let quit = MenuItemBuilder::with_id("quit", "Quit OpenTron").build(app)?;

            let menu = MenuBuilder::new(app)
                .item(&show)
                .separator()
                .item(&health)
                .separator()
                .item(&quit)
                .build()?;

            let _tray = TrayIconBuilder::with_id("main")
                .icon(app.default_window_icon().unwrap().clone())
                .tooltip("OpenTron")
                .menu(&menu)
                .on_menu_event(move |app, event| match event.id().as_ref() {
                    "show" => {
                        if let Some(window) = app.get_webview_window("main") {
                            if window.is_visible().unwrap_or(false) {
                                let _ = window.hide();
                            } else {
                                let _ = window.show();
                                let _ = window.set_focus();
                            }
                        }
                    }
                    "quit" => {
                        app.exit(0);
                    }
                    _ => {}
                })
                .build(app)?;

            // Auto-start Java backend on launch
            let b = boot_backend_ref.clone();
            tauri::async_runtime::spawn(async move {
                if let Err(e) = boot_backend_with_retries(b).await {
                    eprintln!("Failed to start backend: {}", e);
                }
            });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_api_base,
            start_backend,
            stop_backend,
            check_health,
            get_backend_boot_status,
            fetch_models,
            fetch_server_info,
            fetch_savings,
            fetch_agents,
            open_external_url,
        ])
        .build(tauri::generate_context!())
        .expect("error while building OpenTron Desktop")
        .run(move |_app, event| {
            if let tauri::RunEvent::ExitRequested { .. } = event {
                let b = backend.clone();
                tauri::async_runtime::spawn(async move {
                    b.lock().await.stop_all().await;
                });
            }
        });
}
