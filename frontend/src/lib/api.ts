import type { ModelInfo, SavingsData, ServerInfo } from '../types';

// Supabase config
const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL || 'https://mtbtgpwzrbostweaanpr.supabase.co';
const SUPABASE_ANON_KEY = import.meta.env.VITE_SUPABASE_ANON_KEY || '[REDACTED]';

declare global {
  interface Window {
    __TAURI_INTERNALS__?: unknown;
  }
}

export const isTauri = () => typeof window !== 'undefined' && !!window.__TAURI_INTERNALS__;

let _tauriApiBase: string | null = null;

export async function initApiBase(): Promise<void> {
  if (!isTauri()) return;
  try {
    const { invoke } = await import('@tauri-apps/api/core');
    _tauriApiBase = await invoke<string>('get_api_base');
  } catch {
    // ignore
  }
}

const DESKTOP_API_FALLBACK = 'http://127.0.0.1:8000';

interface OpentronSettings {
  apiUrl?: string;
  apiKey?: string;
  [key: string]: unknown;
}

/**
 * Shared helper to load Opentron settings from localStorage
 * Avoids duplication and centralizes error handling
 */
const loadSettings = (): OpentronSettings => {
  try {
    const raw = localStorage.getItem('OpenTron-settings');
    if (raw) {
      return JSON.parse(raw) as OpentronSettings;
    }
  } catch (e) {
    console.error('[api] Failed to load settings from localStorage', e);
  }
  return {};
};

const getSettingsApiUrl = (): string => {
  const settings = loadSettings();
  if (settings.apiUrl) {
    return settings.apiUrl.replace(/\/+$/, '');
  }
  return '';
};

export const getBase = (): string => {
  const settingsUrl = getSettingsApiUrl();
  if (settingsUrl) return settingsUrl;
  if (import.meta.env.VITE_API_URL) return import.meta.env.VITE_API_URL;
  if (isTauri()) return _tauriApiBase || DESKTOP_API_FALLBACK;
  return '';
};

export const getApiKey = (): string => {
  const settings = loadSettings();
  if (settings.apiKey) {
    return String(settings.apiKey);
  }
  if (import.meta.env.VITE_OPENTRON_API_KEY) {
    return import.meta.env.VITE_OPENTRON_API_KEY as string;
  }
  return '';
};

export const authHeaders = (extra?: Record<string, string>): Headers => {
  const headers = new Headers(extra || {});
  const key = getApiKey();
  if (key) {
    headers.set('Authorization', `Bearer ${key}`);
  }
  return headers;
};

function getCloudApiKeys(): Record<string, string> {
  const keys: Record<string, string> = {};

  try {
    const openaiKey = localStorage.getItem('OpenTron-openai-key');
    if (openaiKey) keys.openai = openaiKey;

    const anthropicKey = localStorage.getItem('OpenTron-anthropic-key');
    if (anthropicKey) keys.anthropic = anthropicKey;

    const geminiKey = localStorage.getItem('OpenTron-gemini-key');
    if (geminiKey) keys.google = geminiKey;

    const openrouterKey = localStorage.getItem('OpenTron-openrouter-key');
    if (openrouterKey) keys.openrouter = openrouterKey;
  } catch (e) {
    console.error('[api] Failed to read cloud API keys from localStorage', e);
  }

  return keys;
}

export const apiFetch = (path: string, init: RequestInit = {}): Promise<Response> => {
  // Normalize headers to Headers object for proper merging
  const headers = new Headers(init.headers);
  const authHead = authHeaders();

  // Merge auth headers with request headers
  authHead.forEach((value, key) => {
    headers.set(key, value);
  });

  const cloudApiKeys = getCloudApiKeys();
  if (Object.keys(cloudApiKeys).length > 0) {
    headers.set('X-API-Keys', JSON.stringify(cloudApiKeys));
  }

  // Pass through AbortSignal if provided
  const signal = init.signal;

  return fetch(`${getBase()}${path}`, { ...init, headers, signal });
};

async function tauriInvoke<T>(command: string, args: Record<string, unknown> = {}): Promise<T> {
  const { invoke } = await import('@tauri-apps/api/core');
  const apiUrl = getBase();
  return invoke<T>(command, { apiUrl, ...args });
}

// Setup status
export interface SetupStatus {
  phase: string;
  detail: string;
  ollama_ready: boolean;
  server_ready: boolean;
  model_ready: boolean;
  error: string | null;
  source?: 'ollama' | 'custom';
}

export interface BackendBootStatus {
  state: 'starting' | 'ready' | 'failed';
  message: string | null;
}

export async function getSetupStatus(): Promise<SetupStatus | null> {
  if (!isTauri()) return null;
  try {
    const { invoke } = await import('@tauri-apps/api/core');
    return await invoke<SetupStatus>('get_setup_status');
  } catch {
    return null;
  }
}

// Models
export async function fetchModels(): Promise<ModelInfo[]> {
  // Always use HTTP API
  const res = await apiFetch(`/v1/models`);
  if (!res.ok) throw new Error(`Failed to fetch models: ${res.status}`);
  const data = await res.json();
  return data.data || [];
}

export async function fetchRecommendedModel(): Promise<{ model: string; reason: string }> {
  const res = await apiFetch(`/v1/recommended-model`);
  if (!res.ok) return { model: '', reason: 'Failed to fetch' };
  return res.json();
}

export async function pullModel(modelName: string): Promise<string> {
  // Always use HTTP API - Tauri command not available
  const res = await apiFetch(`/v1/models/pull`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ model: modelName }),
  });
  if (!res.ok) {
    const detail = await res.text().catch(() => res.statusText);
    throw new Error(`Failed to pull model: ${detail}`);
  }
  const body = await res.json().catch(() => null);
  return body?.job_id || '';
}

export async function getPullStatus(jobId: string): Promise<any> {
  const res = await apiFetch(`/v1/models/pull/${encodeURIComponent(jobId)}`);
  if (!res.ok) throw new Error(`Failed to get pull status: ${res.status}`);
  return res.json();
}

export async function deleteModel(modelName: string): Promise<void> {
  if (isTauri()) {
    try {
      const { invoke } = await import('@tauri-apps/api/core');
      await invoke('delete_ollama_model', { modelName });
      return;
    } catch (e: any) {
      throw new Error(e?.message || e || 'Delete failed');
    }
  }
  const res = await apiFetch(`/v1/models/${encodeURIComponent(modelName)}`, {
    method: 'DELETE',
  });
  if (!res.ok) {
    const detail = await res.text().catch(() => res.statusText);
    throw new Error(`Failed to delete model: ${detail}`);
  }
}

const _CLOUD_PREFIXES = ['gpt-', 'o1-', 'o3-', 'o4-', 'claude-', 'gemini-', 'openrouter/'];

export async function preloadModel(modelName: string): Promise<void> {
  if (_CLOUD_PREFIXES.some(p => modelName.startsWith(p))) {
    return;
  }
  const ollamaUrl = 'http://127.0.0.1:11434';
  try {
    const res = await fetch(`${ollamaUrl}/api/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: modelName, prompt: '', keep_alive: '5m' }),
      signal: AbortSignal.timeout(120_000),
    });
    if (!res.ok) throw new Error(`Preload failed: ${res.status}`);
  } catch (e: any) {
    if (e.name === 'TimeoutError') throw new Error('Model load timed out (120s)');
    throw e;
  }
}

// Server info
export async function fetchSavings(): Promise<SavingsData> {
  const res = await apiFetch(`/v1/savings`);
  if (!res.ok) throw new Error(`Failed to fetch savings: ${res.status}`);
  return res.json();
}

export async function fetchServerInfo(): Promise<ServerInfo> {
  const res = await apiFetch(`/v1/info`);
  if (!res.ok) throw new Error(`Failed to fetch server info: ${res.status}`);
  return res.json();
}

export async function checkHealth(): Promise<boolean> {
  if (isTauri()) {
    try {
      return await tauriInvoke<boolean>('check_health', { apiUrl: getBase() });
    } catch {
      return false;
    }
  }
  const probe = async (url: string): Promise<boolean> => {
    try {
      const res = await fetch(url, { cache: 'no-store' });
      return res.ok;
    } catch {
      return false;
    }
  };
  if (await probe('/health')) return true;
  return probe('/v1/connectors');
}

export async function getBackendBootStatus(): Promise<BackendBootStatus | null> {
  if (!isTauri()) return null;
  try {
    const { invoke } = await import('@tauri-apps/api/core');
    return await invoke<BackendBootStatus>('get_backend_boot_status');
  } catch {
    return null;
  }
}

export async function fetchEnergy(): Promise<unknown> {
  if (isTauri()) {
    try {
      return await tauriInvoke('fetch_energy', { apiUrl: getBase() });
    } catch {}
  }
  const res = await apiFetch(`/v1/telemetry/energy`);
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
}

export async function fetchTelemetry(): Promise<unknown> {
  if (isTauri()) {
    try {
      return await tauriInvoke('fetch_telemetry', { apiUrl: getBase() });
    } catch {}
  }
  const res = await apiFetch(`/v1/telemetry/stats`);
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
}

export async function fetchTraces(limit: number = 50): Promise<unknown> {
  if (isTauri()) {
    try {
      return await tauriInvoke('fetch_traces', { apiUrl: getBase(), limit });
    } catch {}
  }
  const res = await apiFetch(`/v1/traces?limit=${limit}`);
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
}

// Speech interfaces
export interface TranscriptionResult {
  text: string;
  language: string | null;
  confidence: number | null;
  duration_seconds: number;
}

export interface SpeechHealth {
  available?: boolean;
  backend?: string;
  voice?: string;
  reason?: string;
  status?: string;
}

export async function transcribeAudio(audioBlob: Blob, filename = 'recording.webm'): Promise<TranscriptionResult> {
  if (isTauri()) {
    try {
      const buffer = await audioBlob.arrayBuffer();
      return await tauriInvoke<TranscriptionResult>('transcribe_audio', {
        audioData: Array.from(new Uint8Array(buffer)),
        filename,
      });
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      throw new Error(msg || 'Transcription failed');
    }
  }
  const formData = new FormData();
  formData.append('file', audioBlob, filename);
  const res = await apiFetch(`/v1/speech/transcribe`, {
    method: 'POST',
    body: formData,
  });
  if (!res.ok) {
    let detail = "";
    try {
      const body = await res.json();
      detail = typeof body.detail === 'string' ? body.detail : "";
    } catch {
      //
    }
    throw new Error(detail || `Transcription failed: ${res.status}`);
  }
  return res.json();
}

export async function fetchSpeechHealth(): Promise<SpeechHealth> {
  if (isTauri()) {
    try {
      return await tauriInvoke<SpeechHealth>('speech_health');
    } catch {
      return { available: false };
    }
  }
  const res = await apiFetch(`/v1/jarvis/health`);
  if (!res.ok) return { available: false };
  return res.json();
}

export interface JarvisSynthesisResult {
  status: string;
  audio_base64?: string;
  voice_id?: string;
  audio_format?: string;
  text_length?: number;
  duration_estimate_ms?: number;
  error_message?: string;
}

export async function synthesizeWithJarvis(text: string): Promise<JarvisSynthesisResult> {
  if (!text || !text.trim()) {
    throw new Error('Text cannot be empty');
  }
  
  const res = await apiFetch(`/v1/jarvis/speak`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text }),
  });
  
  if (!res.ok) {
    let detail = '';
    try {
      const body = await res.json();
      detail = typeof body.error_message === 'string' ? body.error_message : '';
    } catch {}
    throw new Error(detail || `Jarvis TTS failed: ${res.status}`);
  }
  
  return res.json();
}

// Agent/Memory types
export interface ManagedAgent {
  id: string;
  name: string;
  agent_type: string;
  status: string;
  template_id: string;
  instruction: string;
  model: string;
  schedule_type: string;
  schedule_value?: string;
  tools: string[];
  input_tokens: number;
  output_tokens: number;
  created_at: number;
  updated_at: number;
  last_run_at?: number;
  total_runs: number;
  total_cost: number;
  learning_enabled: boolean;
  summary_memory: string;
  current_activity?: string;
  config?: Record<string, any>;
  [key: string]: any;
}

export interface AgentTask {
  id: string;
  [key: string]: any;
}

export interface ChannelBinding {
  id: string;
  channel_type: string;
  routing_mode?: string;
  config?: Record<string, any>;
  [key: string]: any;
}

export interface AgentTemplate {
  id: string;
  name: string;
  description: string;
  [key: string]: any;
}

export interface LearningLogEntry {
  id: string;
  event_type: string;
  description?: string;
  created_at: number;
  [key: string]: any;
}

export interface AgentTrace {
  id: string;
  started_at: number;
  duration: number;
  outcome: string;
  steps: number;
  metadata?: Record<string, any>;
  [key: string]: any;
}

export interface AgentTraceDetail {
  id: string;
  steps: Array<{
    step_type: string;
    input?: Record<string, any>;
    output?: any;
    duration?: number;
  }>;
  outcome: string;
  [key: string]: any;
}

export interface ToolInfo {
  name: string;
  category: string;
  source?: string;
  configured: boolean;
  credential_keys?: string[];
  description?: string;
  [key: string]: any;
}

export interface PendingApproval {
  id: string;
  [key: string]: any;
}

export interface AgentMessage {
  id: string;
  [key: string]: any;
}

export interface MemoryStats {
  entries: number;
  backend: string;
  [key: string]: any;
}

export interface MemorySearchResult {
  content: string;
  [key: string]: any;
}

export interface InferenceSource {
  kind: 'ollama' | 'custom';
  [key: string]: any;
}

// Managed Agents API
export async function fetchManagedAgents(): Promise<ManagedAgent[]> {
  const res = await apiFetch(`/v1/managed-agents`);
  if (!res.ok) throw new Error(`Failed to fetch agents: ${res.status}`);
  const data = await res.json();
  return data.agents || [];
}

export async function fetchManagedAgent(id: string): Promise<ManagedAgent> {
  const res = await apiFetch(`/v1/managed-agents/${id}`);
  if (!res.ok) throw new Error(`Failed to fetch agent: ${res.status}`);
  return res.json();
}

export async function createManagedAgent(body: any): Promise<ManagedAgent> {
  const res = await apiFetch(`/v1/managed-agents`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`Failed to create agent: ${res.status}`);
  return res.json();
}

export async function updateManagedAgent(id: string, body: any): Promise<ManagedAgent> {
  const res = await apiFetch(`/v1/managed-agents/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`Failed to update agent: ${res.status}`);
  return res.json();
}

export async function deleteManagedAgent(id: string): Promise<void> {
  const res = await apiFetch(`/v1/managed-agents/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`Failed to delete agent: ${res.status}`);
}

export async function pauseManagedAgent(id: string): Promise<void> {
  const res = await apiFetch(`/v1/managed-agents/${id}/pause`, { method: 'POST' });
  if (!res.ok) throw new Error(`Failed to pause agent: ${res.status}`);
}

export async function resumeManagedAgent(id: string): Promise<void> {
  const res = await apiFetch(`/v1/managed-agents/${id}/resume`, { method: 'POST' });
  if (!res.ok) throw new Error(`Failed to resume agent: ${res.status}`);
}

export async function runManagedAgent(id: string): Promise<void> {
  const res = await apiFetch(`/v1/managed-agents/${id}/run`, { method: 'POST' });
  if (!res.ok) throw new Error(`Failed to run agent: ${res.status}`);
}

export async function recoverManagedAgent(id: string): Promise<any> {
  const res = await apiFetch(`/v1/managed-agents/${id}/recover`, { method: 'POST' });
  if (!res.ok) throw new Error(`Failed to recover agent: ${res.status}`);
  return res.json();
}

export async function fetchAgentState(id: string): Promise<any> {
  const res = await apiFetch(`/v1/managed-agents/${id}/state`);
  if (!res.ok) throw new Error(`Failed to fetch agent state: ${res.status}`);
  return res.json();
}

export async function fetchAgentTasks(id: string): Promise<AgentTask[]> {
  const res = await apiFetch(`/v1/managed-agents/${id}/tasks`);
  if (!res.ok) return [];
  const data = await res.json();
  return data.tasks || [];
}

export async function createAgentTask(id: string, desc: string): Promise<AgentTask> {
  const res = await apiFetch(`/v1/managed-agents/${id}/tasks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ description: desc }),
  });
  if (!res.ok) throw new Error(`Failed to create task: ${res.status}`);
  return res.json();
}

export async function fetchAgentChannels(id: string): Promise<ChannelBinding[]> {
  const res = await apiFetch(`/v1/managed-agents/${id}/channels`);
  if (!res.ok) return [];
  const data = await res.json();
  return data.bindings || [];
}

export async function bindAgentChannel(id: string, type: string, config?: any): Promise<ChannelBinding> {
  const res = await apiFetch(`/v1/managed-agents/${id}/channels/bind`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ channel_type: type, config: config || {} }),
  });
  if (!res.ok) throw new Error(`Failed to bind channel: ${res.status}`);
  return res.json();
}

export async function unbindAgentChannel(id: string, binding: string): Promise<void> {
  const res = await apiFetch(`/v1/managed-agents/${id}/channels/${binding}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`Failed to unbind channel: ${res.status}`);
}

export async function fetchTemplates(): Promise<AgentTemplate[]> {
  const res = await apiFetch(`/v1/agent-templates`);
  if (!res.ok) return [];
  const data = await res.json();
  return data.templates || [];
}

export async function askAgent(id: string, content: string): Promise<AgentMessage> {
  const res = await apiFetch(`/v1/managed-agents/${id}/ask`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question: content }),
  });
  if (!res.ok) throw new Error(`Failed to ask agent: ${res.status}`);
  return res.json();
}

export async function fetchAgentMessages(id: string): Promise<AgentMessage[]> {
  const res = await apiFetch(`/v1/managed-agents/${id}/messages`);
  if (!res.ok) return [];
  const data = await res.json();
  return data.messages || [];
}

export async function sendAgentMessage(id: string, content: string, mode?: any, callbacks?: any): Promise<AgentMessage> {
  const res = await apiFetch(`/v1/managed-agents/${id}/ask`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question: content, mode, callbacks }),
  });
  if (!res.ok) throw new Error(`Failed to send message: ${res.status}`);
  return res.json();
}

export async function fetchErrorAgents(): Promise<ManagedAgent[]> {
  const res = await apiFetch(`/v1/managed-agents?status=error`);
  if (!res.ok) return [];
  const data = await res.json();
  return data.agents || [];
}

export async function fetchLearningLog(id: string): Promise<LearningLogEntry[]> {
  const res = await apiFetch(`/v1/managed-agents/${id}/learning`);
  if (!res.ok) return [];
  const data = await res.json();
  return data.logs || [];
}

export async function triggerLearning(id: string): Promise<void> {
  const res = await apiFetch(`/v1/managed-agents/${id}/learning/trigger`, { method: 'POST' });
  if (!res.ok) throw new Error(`Failed to trigger learning: ${res.status}`);
}

export async function fetchAgentTraces(id: string, limit: number = 50): Promise<AgentTrace[]> {
  const res = await apiFetch(`/v1/managed-agents/${id}/traces?limit=${limit}`);
  if (!res.ok) return [];
  const data = await res.json();
  return data.traces || [];
}

export async function fetchAgentTrace(id: string, traceId: string): Promise<AgentTraceDetail> {
  const res = await apiFetch(`/v1/managed-agents/${id}/traces/${traceId}`);
  if (!res.ok) throw new Error(`Failed to fetch trace: ${res.status}`);
  return res.json();
}

export async function fetchAvailableTools(): Promise<ToolInfo[]> {
  const res = await apiFetch(`/v1/tools`);
  if (!res.ok) return [];
  const data = await res.json();
  return data.tools || [];
}

export async function saveToolCredentials(tool: string, creds: any): Promise<void> {
  const res = await apiFetch(`/v1/tools/${tool}/credentials`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(creds),
  });
  if (!res.ok) throw new Error(`Failed to save credentials: ${res.status}`);
}

// Remaining stubs for memory/approval endpoints (not needed for agents to work)
export async function fetchPendingApprovals(): Promise<PendingApproval[]> { return []; }
export async function approveAction(id: string): Promise<void> { }
export async function denyAction(id: string): Promise<void> { }

export async function getMemoryStats(): Promise<MemoryStats> { throw new Error('Not implemented'); }
export async function searchMemory(query: string, topK?: number): Promise<MemorySearchResult[]> { return []; }
export async function storeMemory(content: string, metadata?: any): Promise<void> { }
export async function indexMemoryPath(path: string): Promise<any> { throw new Error('Not implemented'); }
export async function getMemoryConfig(): Promise<any> { throw new Error('Not implemented'); }

export async function getInferenceSource(): Promise<InferenceSource> { throw new Error('Not implemented'); }
export async function setInferenceSource(src: any): Promise<void> { }

export async function submitSavings(data: any): Promise<boolean> {
  if (!SUPABASE_URL || !SUPABASE_ANON_KEY) return false;
  try {
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/savings_entries?on_conflict=anon_id`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          apikey: SUPABASE_ANON_KEY,
          Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
          Prefer: 'resolution=merge-duplicates',
        },
        body: JSON.stringify(data),
      },
    );
    return res.ok || res.status === 201 || res.status === 200;
  } catch {
    return false;
  }
}

// Channel setup functions
export async function sendblueVerify(id: string, secret: string): Promise<any> { throw new Error('Not implemented'); }
export async function sendblueRegisterWebhook(id: string, secret: string, url: string): Promise<any> { throw new Error('Not implemented'); }
export async function sendblueTest(id: string, secret: string, from: string, to: string): Promise<any> { throw new Error('Not implemented'); }
export async function sendblueHealth(): Promise<any> { throw new Error('Not implemented'); }

// Multi-Agent Coordinator API
// Multi-Agent Coordinator API with improved error handling
async function handleApiError(res: Response, action: string): Promise<never> {
  try {
    const errorData = await res.json();
    throw {
      status: res.status,
      message: errorData.error || `${action}: ${res.status}`,
      response: { data: errorData }
    };
  } catch (e: any) {
    if (e.status) throw e;
    throw {
      status: res.status,
      message: `${action}: HTTP ${res.status}`,
      response: { data: {} }
    };
  }
}

export async function coordinateAgents(
  request: string,
  context?: string,
  onEvent?: (event: { type: string; agent?: string; message?: string; preview?: string; error?: string; result?: any; elapsed_ms?: number; chunk?: string }) => void
): Promise<any> {
  try {
    const res = await apiFetch(`/v1/agents/coordinate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ request, context: context || '' }),
    });
    if (!res.ok) await handleApiError(res, 'Failed to coordinate agents');

    const reader = res.body?.getReader();
    const decoder = new TextDecoder();
    let finalResult: any = null;
    let buffer = '';

    if (reader) {
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          // Flush remaining buffer on stream close (handles truncated last chunk)
          if (buffer.trim()) {
            for (const line of buffer.split('\n')) {
              const trimmed = line.trim();
              const jsonStr = trimmed.startsWith('data:data: ') ? trimmed.slice(11)
                            : trimmed.startsWith('data: ')      ? trimmed.slice(6)
                            : trimmed.startsWith('data:')       ? trimmed.slice(5)
                            : trimmed.startsWith('{')           ? trimmed  // raw JSON
                            : null;
              if (jsonStr) {
                try {
                  const data = JSON.parse(jsonStr);
                  if (onEvent) onEvent(data);
                  if (data.type === 'done') finalResult = data;
                } catch {}
              }
            }
          }
          break;
        }
        buffer += decoder.decode(value, { stream: true });

        // Always parse as SSE — split on double newline (event boundary)
        const events = buffer.split('\n\n');
        buffer = events.pop() ?? '';
        for (const event of events) {
          for (const line of event.split('\n')) {
            const trimmed = line.trim();
            const jsonStr = trimmed.startsWith('data:data: ') ? trimmed.slice(11)
                          : trimmed.startsWith('data: ')      ? trimmed.slice(6)
                          : trimmed.startsWith('data:')       ? trimmed.slice(5)
                          : null;
            if (jsonStr) {
              try {
                const data = JSON.parse(jsonStr);
                if (onEvent) onEvent(data);
                if (data.type === 'done') finalResult = data;
              } catch {}
            }
          }
        }
      }
    }

    return finalResult;
  } catch (error: any) {
    if (error.response) throw error;
    throw { response: { data: { error: error.message || 'Unknown error' } } };
  }
}

export async function getAgentStatuses(): Promise<any> {
  try {
    const res = await apiFetch(`/v1/agents/status`);
    if (!res.ok) await handleApiError(res, 'Failed to get agent statuses');
    return res.json();
  } catch (error: any) {
    if (error.response) throw error;
    throw { response: { data: { error: error.message || 'Unknown error' } } };
  }
}

export async function sendAgentTask(agent: string, task: string): Promise<any> {
  try {
    const res = await apiFetch(`/v1/agents/task`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agent, task }),
    });
    if (!res.ok) await handleApiError(res, 'Failed to send agent task');
    return res.json();
  } catch (error: any) {
    if (error.response) throw error;
    throw { response: { data: { error: error.message || 'Unknown error' } } };
  }
}

// PostgreSQL Storage API
export async function getStorageStats(): Promise<any> {
  try {
    const res = await apiFetch(`/v1/agents/storage/stats`);
    if (!res.ok) throw new Error(`Failed to fetch storage stats: ${res.status}`);
    return res.json();
  } catch (error: any) {
    console.error('[API] Error fetching storage stats:', error);
    return { total_memories: 0, total_traces: 0, backend: 'postgresql', error: error.message };
  }
}

export async function getStorageTraces(agentId: string, limit: number = 50): Promise<any> {
  try {
    const res = await apiFetch(`/v1/agents/storage/traces/${agentId}?limit=${limit}`);
    if (!res.ok) throw new Error(`Failed to fetch storage traces: ${res.status}`);
    return res.json();
  } catch (error: any) {
    console.error('[API] Error fetching storage traces:', error);
    return { traces: [], count: 0, error: error.message };
  }
}

export async function getMemoryStatsDetailed(): Promise<any> {
  try {
    const res = await apiFetch(`/v1/memory/stats/detailed`);
    if (!res.ok) throw new Error(`Failed to fetch memory stats: ${res.status}`);
    return res.json();
  } catch (error: any) {
    console.error('[API] Error fetching memory stats:', error);
    return { total_memories: 0, total_traces: 0, backend: 'postgresql', error: error.message };
  }
}

export async function storeAgentMemory(agentName: string, content: string, summary?: string): Promise<any> {
  try {
    const res = await apiFetch(`/v1/memory/store`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agent_name: agentName, content, summary: summary || '' }),
    });
    if (!res.ok) throw new Error(`Failed to store memory: ${res.status}`);
    return res.json();
  } catch (error: any) {
    console.error('[API] Error storing memory:', error);
    throw error;
  }
}

export async function searchAgentMemory(query: string, agentName?: string, topK: number = 5): Promise<any> {
  try {
    const res = await apiFetch(`/v1/memory/search`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, agent_name: agentName || '', top_k: topK }),
    });
    if (!res.ok) throw new Error(`Failed to search memory: ${res.status}`);
    return res.json();
  } catch (error: any) {
    console.error('[API] Error searching memory:', error);
    return { results: [], error: error.message };
  }
}

export async function getAgentMemory(agentId: string, limit: number = 50): Promise<any> {
  try {
    const res = await apiFetch(`/v1/memory/agent/${agentId}?limit=${limit}`);
    if (!res.ok) throw new Error(`Failed to fetch agent memory: ${res.status}`);
    return res.json();
  } catch (error: any) {
    console.error('[API] Error fetching agent memory:', error);
    return { memories: [], count: 0, error: error.message };
  }
}

export async function getAgentTraces(agentId: string, limit: number = 50): Promise<any> {
  try {
    const res = await apiFetch(`/v1/traces/agent/${agentId}?limit=${limit}`);
    if (!res.ok) throw new Error(`Failed to fetch agent traces: ${res.status}`);
    return res.json();
  } catch (error: any) {
    console.error('[API] Error fetching agent traces:', error);
    return { traces: [], count: 0, error: error.message };
  }
}

export async function analyzeScreenshot(imageBase64: string, question: string = 'Analyze this screenshot and suggest improvements', context: string = ''): Promise<any> {
  try {
    const res = await apiFetch('/v1/analyze-screenshot', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 
        image_base64: imageBase64, 
        question, 
        context 
      }),
    });
    if (!res.ok) throw new Error(`Screenshot analysis failed: ${res.status}`);
    return res.json();
  } catch (error: any) {
    console.error('[API] Screenshot analysis error:', error);
    throw error;
  }
}
