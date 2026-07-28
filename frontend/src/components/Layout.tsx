import { useEffect, useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { ApprovalBell } from './ApprovalBell';
import { Sidebar } from './Sidebar/Sidebar';
import { SystemPulse } from './SystemPulse';
import { useAppStore } from '../lib/store';
import { checkHealth, getBackendBootStatus, isTauri } from '../lib/api';

export function Layout() {
  const sidebarOpen = useAppStore((s) => s.sidebarOpen);
  const [apiReachable, setApiReachable] = useState<boolean | null>(null);
  const [bootState, setBootState] = useState<'starting' | 'ready' | 'failed' | null>(null);
  const manualBackendStartCommand = '"%LOCALAPPDATA%\\OpenTron\\sidecar\\jre\\bin\\java.exe" -Dspring.profiles.active=embedded -jar "%LOCALAPPDATA%\\OpenTron\\sidecar\\backend.jar" --server.port=8000';
  const [copyFeedback, setCopyFeedback] = useState<'idle' | 'copied' | 'failed'>('idle');

  useEffect(() => {
    const check = async () => {
      const healthy = await checkHealth();
      setApiReachable(healthy);

      if (isTauri()) {
        const status = await getBackendBootStatus();
        setBootState(status?.state ?? null);
      }
    };

    void check();
    const interval = setInterval(() => {
      void check();
    }, 5000);
    const onFocus = () => {
      void check();
    };
    window.addEventListener('focus', onFocus);
    return () => {
      clearInterval(interval);
      window.removeEventListener('focus', onFocus);
    };
  }, []);

  const navigate = useNavigate();

  const showBackendConnectionError = isTauri()
    ? bootState === 'failed' || (bootState === 'ready' && apiReachable === false)
    : apiReachable === false;

  const copyManualCommand = async () => {
    try {
      await navigator.clipboard.writeText(manualBackendStartCommand);
      setCopyFeedback('copied');
    } catch {
      setCopyFeedback('failed');
    }
    setTimeout(() => setCopyFeedback('idle'), 2000);
  };

  return (
    <div className="flex flex-col h-full w-full overflow-hidden relative" style={{ paddingTop: '3px' }}>
      <div className="hud-backdrop" aria-hidden="true" />
      <SystemPulse apiReachable={apiReachable} />
      <ApprovalBell />

      {/* Health check banner */}
      {showBackendConnectionError && (
        <div
          className="px-4 py-2 text-sm shrink-0"
          style={{
            background: 'color-mix(in srgb, var(--color-error) 8%, transparent)',
            borderBottom: '1px solid color-mix(in srgb, var(--color-error) 15%, transparent)',
            color: 'var(--color-text)',
          }}
        >
          <div className="flex items-center gap-3">
            <span
              className="w-1.5 h-1.5 rounded-full shrink-0"
              style={{ background: 'var(--color-error)' }}
            />
            <span>Cannot connect to backend</span>
            <button
              onClick={() => navigate('/settings')}
              className="text-sm underline cursor-pointer ml-auto shrink-0"
              style={{ color: 'var(--color-accent)' }}
            >
              Change URL
            </button>
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <span style={{ color: 'var(--color-text-secondary)' }}>Run manually:</span>
            <code
              className="px-2 py-1 rounded text-xs break-all select-text"
              style={{
                background: 'color-mix(in srgb, var(--color-error) 6%, var(--color-bg-secondary))',
                border: '1px solid color-mix(in srgb, var(--color-error) 20%, transparent)',
              }}
            >
              {manualBackendStartCommand}
            </code>
            <button
              onClick={() => void copyManualCommand()}
              className="px-2 py-1 rounded text-xs cursor-pointer"
              style={{
                border: '1px solid var(--color-border)',
                background: 'var(--color-bg-secondary)',
                color: 'var(--color-text)',
              }}
            >
              {copyFeedback === 'copied' ? 'Copied' : copyFeedback === 'failed' ? 'Copy failed' : 'Copy command'}
            </button>
          </div>
        </div>
      )}

      <div className="flex flex-1 min-h-0 relative z-10">
        <Sidebar />
        {sidebarOpen && (
          <div
            className="fixed inset-0 z-20 bg-black/40 md:hidden"
            onClick={() => useAppStore.getState().setSidebarOpen(false)}
          />
        )}
        <main className="flex-1 flex flex-col min-w-0 h-full relative overflow-hidden" style={{ background: 'transparent' }}>
          <div className="flex-1 flex flex-col min-w-0 min-h-0 relative z-[2]">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}

