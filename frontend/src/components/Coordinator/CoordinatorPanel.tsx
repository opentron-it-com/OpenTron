import { useState, useEffect, useRef } from 'react';
import { coordinateAgents, getAgentStatuses, sendAgentTask } from '../../lib/api';
import { toast } from 'sonner';
import { Send, Loader2, Brain, Zap, AlertCircle, CheckCircle2 } from 'lucide-react';
import ReactMarkdown from 'react-markdown';

interface AgentStatusInfo {
  name: string;
  skills: string[];
  last_executed_ms: number;
}

interface TaskResult {
  status: 'pending' | 'completed' | 'error';
  task_id?: string;
  error?: string;
  message?: string;
  poll_url?: string;
  retry_after_ms?: number;
}

export function CoordinatorPanel() {
  const [request, setRequest] = useState('');
  const [context, setContext] = useState('');
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [agentStatuses, setAgentStatuses] = useState<Record<string, AgentStatusInfo>>({});
  const [loadingStatuses, setLoadingStatuses] = useState(true);
  const [selectedAgent, setSelectedAgent] = useState<string>('');
  const [agentTask, setAgentTask] = useState('');
  const [sendingTask, setSendingTask] = useState(false);
  const [taskId, setTaskId] = useState<string | null>(null);
  const [taskResult, setTaskResult] = useState<TaskResult | null>(null);
  const pollingRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Load agent statuses on mount
  useEffect(() => {
    const loadStatuses = async () => {
      try {
        const response = await getAgentStatuses();
        setAgentStatuses(response.agents || {});
      } catch (error: any) {
        const errorMsg = error?.response?.data?.error || error?.message || 'Failed to load agent statuses';
        toast.error(errorMsg);
        console.error('Failed to load statuses:', error);
      } finally {
        setLoadingStatuses(false);
      }
    };

    loadStatuses();
    const interval = setInterval(loadStatuses, 30000);
    return () => clearInterval(interval);
  }, []);

  // Cleanup polling on unmount
  useEffect(() => {
    return () => {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
      }
    };
  }, []);

  const handleCoordinate = async () => {
    if (!request.trim()) {
      toast.error('Please enter a request');
      return;
    }

    if (context.length > 3000) {
      toast.error('Context too long (max 3000 characters)');
      return;
    }

    setLoading(true);
    try {
      const response = await coordinateAgents(request, context);
      
      if (!response.result) {
        throw new Error('Invalid response format from coordinator');
      }
      
      setResult(response);
      setRequest('');
      setContext('');
      toast.success(`Coordinator processed request in ${response.elapsed_ms}ms`);
    } catch (error: any) {
      const errorMsg = error?.response?.data?.error || error?.message || 'Failed to coordinate agents';
      toast.error(errorMsg);
      console.error('Coordinator error:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSendTask = async () => {
    if (!selectedAgent || !agentTask.trim()) {
      toast.error('Select an agent and enter a task');
      return;
    }

    setSendingTask(true);
    try {
      const response = await sendAgentTask(selectedAgent, agentTask);
      
      if (response.task_id) {
        setTaskId(response.task_id);
        setTaskResult({ status: 'pending', task_id: response.task_id, message: 'Task queued...' });
        setAgentTask('');
        toast.success(`Task queued: ${response.task_id}`);
        
        startPollingTask(response.task_id);
      } else {
        throw new Error('No task ID returned from server');
      }
    } catch (error: any) {
      const errorMsg = error?.response?.data?.error || error?.message || 'Failed to send task';
      toast.error(errorMsg);
      console.error('Send task error:', error);
    } finally {
      setSendingTask(false);
    }
  };

  const startPollingTask = (tid: string) => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
    }

    let pollCount = 0;
    const maxQuickPolls = 10;

    pollingRef.current = setInterval(async () => {
      try {
        const response = await fetch(`/v1/agents/task/${tid}`);
        const data = await response.json();

        setTaskResult(data);

        if (data.status === 'completed') {
          toast.success(`Task completed: ${data.message}`);
          clearInterval(pollingRef.current!);
          pollingRef.current = null;
        } else if (data.status === 'error') {
          toast.error(`Task failed: ${data.error}`);
          clearInterval(pollingRef.current!);
          pollingRef.current = null;
        }
      } catch (error: any) {
        console.error('Polling error:', error);
      }

      pollCount++;
      
      if (pollCount === maxQuickPolls) {
        clearInterval(pollingRef.current!);
        pollingRef.current = setInterval(async () => {
          // Slower polling
        }, 2000);
      }
    }, 500);
  };

  const cleanCodeContent = (code: string): string => {
    // Remove extra spaces in Java package/import statements: java. util → java.util
    code = code.replace(/([a-zA-Z0-9_]+)\s+\.\s+([a-zA-Z0-9_]+)/g, '$1.$2');
    // Remove extra spaces around dots: java . util . concurrent → java.util.concurrent
    code = code.replace(/\s*\.\s*/g, '.');
    // Clean up multiple spaces in general (but preserve indentation)
    const lines = code.split('\n');
    const cleanedLines = lines.map((line) => {
      const indentation = line?.match(/^\s*/)?.[0] || '';
      const content = line.slice(indentation.length);
      return indentation + content.replace(/\s{2,}/g, ' ');
    });
    return cleanedLines.join('\n');
  };

  const renderAgentResult = (agentResult: any) => {
    if (!agentResult) return null;

    if (agentResult?.error) {
      return <div style={{ color: 'var(--color-error)' }}>Error: {agentResult.error}</div>;
    }

    if (typeof agentResult === 'string') {
      return (
        <ReactMarkdown
          components={{
            pre: (props: any) => (
              <pre {...props} style={{ background: 'var(--color-bg)', padding: '8px', borderRadius: '4px', overflow: 'auto', fontSize: '0.75rem', margin: 0 }} />
            ),
            code: (props: any) => {
              const { node, inline, children, ...rest } = props;
              let content = children?.[0] ?? '';
              
              // Only clean if it's a code block (not inline)
              if (!inline && typeof content === 'string') {
                content = cleanCodeContent(content);
              }
              
              return (
                <code {...rest} style={{ 
                  background: inline ? 'var(--color-bg)' : undefined, 
                  padding: inline ? '2px 4px' : undefined, 
                  borderRadius: '2px',
                  fontFamily: 'monospace',
                  fontSize: inline ? '0.85rem' : '0.75rem',
                  whiteSpace: inline ? 'nowrap' : 'pre-wrap',
                  wordBreak: inline ? 'break-word' : 'normal'
                }}>
                  {content}
                </code>
              );
            },
            h1: (props: any) => <h1 {...props} style={{ fontSize: '1.25rem', fontWeight: 'bold', marginTop: '0.5rem', marginBottom: '0.25rem', color: 'var(--color-text)' }} />,
            h2: (props: any) => <h2 {...props} style={{ fontSize: '1.1rem', fontWeight: 'bold', marginTop: '0.5rem', marginBottom: '0.25rem', color: 'var(--color-text)' }} />,
            h3: (props: any) => <h3 {...props} style={{ fontSize: '1rem', fontWeight: 'bold', marginTop: '0.25rem', marginBottom: '0.125rem', color: 'var(--color-text)' }} />,
            p: (props: any) => <p {...props} style={{ margin: '0.25rem 0', lineHeight: '1.4' }} />,
            li: (props: any) => <li {...props} style={{ marginBottom: '0.125rem', lineHeight: '1.4' }} />,
            ul: (props: any) => <ul {...props} style={{ margin: '0.25rem 0', paddingLeft: '1.25rem' }} />,
            ol: (props: any) => <ol {...props} style={{ margin: '0.25rem 0', paddingLeft: '1.25rem' }} />,
          }}
        >
          {agentResult}
        </ReactMarkdown>
      );
    }

    if (agentResult?.recommendations && Array.isArray(agentResult.recommendations)) {
      return (
        <div>
          {agentResult.is_mock && (
            <div style={{ color: 'var(--color-warning)', fontSize: '0.75rem', marginBottom: '0.5rem' }}>
              ⚡ Mock response (LLM unavailable)
            </div>
          )}
          <ul style={{ paddingLeft: '1.25rem', margin: 0 }}>
            {agentResult.recommendations.map((rec: string, idx: number) => (
              <li key={idx} style={{ marginBottom: '0.5rem', lineHeight: '1.4' }}>
                {rec}
              </li>
            ))}
          </ul>
        </div>
      );
    }

    return <pre style={{ fontSize: '0.75rem', overflow: 'auto', margin: 0 }}>{JSON.stringify(agentResult, null, 2)}</pre>;
  };

  const agentsList = Object.entries(agentStatuses);

  return (
    <div className="space-y-6">
      {/* Main Coordinator Section */}
      <div
        className="rounded-lg p-6"
        style={{
          background: 'var(--color-bg-secondary)',
          border: '1px solid var(--color-border)',
        }}
      >
        <div className="flex items-center gap-2 mb-4">
          <Brain size={18} style={{ color: 'var(--color-accent)' }} />
          <h3 className="text-base font-semibold" style={{ color: 'var(--color-text)' }}>
            Multi-Agent Coordinator
          </h3>
        </div>

        <p
          className="text-sm mb-4"
          style={{ color: 'var(--color-text-secondary)' }}
        >
          Send a request and the coordinator will automatically route it to the right specialist agents. Results appear below.
        </p>

        {/* Request input */}
        <div className="space-y-2 mb-4">
          <label
            className="block text-sm font-medium"
            style={{ color: 'var(--color-text-secondary)' }}
          >
            Your Request
          </label>
          <textarea
            value={request}
            onChange={(e) => setRequest(e.target.value)}
            placeholder="e.g., optimize the cache layer for high throughput scenarios"
            className="w-full px-3 py-2 rounded-lg text-sm bg-transparent resize-none"
            style={{
              border: '1px solid var(--color-border)',
              color: 'var(--color-text)',
              minHeight: '80px',
            }}
            disabled={loading}
          />
        </div>

        {/* Context input */}
        <div className="space-y-2 mb-4">
          <label
            className="block text-sm font-medium"
            style={{ color: 'var(--color-text-secondary)' }}
          >
            Context (optional, max 3000 chars)
          </label>
          <textarea
            value={context}
            onChange={(e) => setContext(e.target.value.slice(0, 3000))}
            placeholder="e.g., we have 2M daily requests and database queries are the bottleneck"
            className="w-full px-3 py-2 rounded-lg text-sm bg-transparent resize-none"
            style={{
              border: '1px solid var(--color-border)',
              color: 'var(--color-text)',
              minHeight: '60px',
            }}
            disabled={loading}
          />
          <div className="text-xs" style={{ color: 'var(--color-text-tertiary)' }}>
            {context.length}/3000
          </div>
        </div>

        {/* Submit button */}
        <button
          onClick={handleCoordinate}
          disabled={loading || !request.trim()}
          className="flex items-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium cursor-pointer transition-opacity"
          style={{
            background: 'var(--color-accent)',
            color: 'var(--color-on-accent)',
            opacity: loading || !request.trim() ? 0.6 : 1,
          }}
        >
          {loading ? (
            <>
              <Loader2 size={14} className="animate-spin" /> Coordinating...
            </>
          ) : (
            <>
              <Zap size={14} /> Coordinate Agents
            </>
          )}
        </button>

        {/* Results */}
        {result && (
          <div className="mt-4 p-4 rounded-lg" style={{ background: 'var(--color-bg)' }}>
            <div className="mb-3 flex items-center gap-2">
              <CheckCircle2 size={16} style={{ color: 'var(--color-success)' }} />
              <span
                className="text-xs px-2 py-1 rounded font-medium"
                style={{
                  background: 'var(--color-success)20',
                  color: 'var(--color-success)',
                }}
              >
                Completed in {result.elapsed_ms}ms
              </span>
            </div>

            {/* Agents used */}
            {result.result?.agents_used && (
              <div className="mb-3">
                <p className="text-xs font-semibold mb-2" style={{ color: 'var(--color-text-secondary)' }}>
                  Agents Used
                </p>
                <div className="flex flex-wrap gap-2">
                  {result.result.agents_used.map((agent: string) => (
                    <span
                      key={agent}
                      className="text-xs px-2.5 py-1 rounded-full font-medium"
                      style={{
                        background: 'var(--color-accent-purple)20',
                        color: 'var(--color-accent-purple)',
                      }}
                    >
                      {agent}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* Results per agent */}
            {result.result?.results && (
              <div className="space-y-2">
                <p className="text-xs font-semibold" style={{ color: 'var(--color-text-secondary)' }}>
                  Results by Agent
                </p>
                {Object.entries(result.result.results).map(([agent, agentResult]: [string, any]) => (
                  <div
                    key={agent}
                    className="p-3 rounded border"
                    style={{
                      background: 'var(--color-bg-secondary)',
                      border: '1px solid var(--color-border)',
                    }}
                  >
                    <div className="font-semibold mb-2" style={{ color: 'var(--color-accent)', fontSize: '0.875rem' }}>
                      {agent.charAt(0).toUpperCase() + agent.slice(1)}
                    </div>
                    <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem', maxHeight: '500px', overflowY: 'auto' }}>
                      {renderAgentResult(agentResult)}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Agent Statuses */}
      <div
        className="rounded-lg p-6"
        style={{
          background: 'var(--color-bg-secondary)',
          border: '1px solid var(--color-border)',
        }}
      >
        <h3 className="text-base font-semibold mb-4" style={{ color: 'var(--color-text)' }}>
          Agent Statuses
        </h3>

        {loadingStatuses ? (
          <div className="text-sm text-center py-8" style={{ color: 'var(--color-text-tertiary)' }}>
            Loading agent statuses...
          </div>
        ) : agentsList.length === 0 ? (
          <div
            className="flex items-center gap-2 py-8 px-4 rounded-lg"
            style={{
              background: 'var(--color-bg)',
              border: '1px solid var(--color-border)',
              color: 'var(--color-warning)',
            }}
          >
            <AlertCircle size={16} />
            <span className="text-sm">No agents available. Ensure backend is running.</span>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            {agentsList.map(([agentName, status]) => (
              <div
                key={agentName}
                className="p-3 rounded-lg"
                style={{
                  background: 'var(--color-bg)',
                  border: '1px solid var(--color-border)',
                }}
              >
                <div className="text-sm font-medium mb-1" style={{ color: 'var(--color-text)' }}>
                  {agentName.charAt(0).toUpperCase() + agentName.slice(1)}
                </div>
                <div className="text-xs mb-2" style={{ color: 'var(--color-text-tertiary)' }}>
                  {status.name}
                </div>
                <div className="space-y-1">
                  {status.skills.slice(0, 3).map((skill) => (
                    <span
                      key={skill}
                      className="text-xs px-2 py-0.5 rounded inline-block"
                      style={{
                        background: 'var(--color-accent)20',
                        color: 'var(--color-accent)',
                      }}
                    >
                      {skill}
                    </span>
                  ))}
                  {status.skills.length > 3 && (
                    <span
                      className="text-xs px-2 py-0.5 rounded inline-block"
                      style={{
                        background: 'var(--color-text-tertiary)20',
                        color: 'var(--color-text-tertiary)',
                      }}
                    >
                      +{status.skills.length - 3} more
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Send Task to Specific Agent */}
      <div
        className="rounded-lg p-6"
        style={{
          background: 'var(--color-bg-secondary)',
          border: '1px solid var(--color-border)',
        }}
      >
        <h3 className="text-base font-semibold mb-4" style={{ color: 'var(--color-text)' }}>
          Send Task to Specific Agent
        </h3>

        <div className="space-y-3">
          <div>
            <label
              className="block text-sm font-medium mb-2"
              style={{ color: 'var(--color-text-secondary)' }}
            >
              Select Agent
            </label>
            <select
              value={selectedAgent}
              onChange={(e) => setSelectedAgent(e.target.value)}
              className="w-full px-3 py-2 rounded-lg text-sm"
              style={{
                background: 'var(--color-bg)',
                border: '1px solid var(--color-border)',
                color: 'var(--color-text)',
              }}
            >
              <option value="">Choose an agent...</option>
              {Object.keys(agentStatuses).map((agent) => (
                <option key={agent} value={agent}>
                  {agent.charAt(0).toUpperCase() + agent.slice(1)}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label
              className="block text-sm font-medium mb-2"
              style={{ color: 'var(--color-text-secondary)' }}
            >
              Task Description
            </label>
            <textarea
              value={agentTask}
              onChange={(e) => setAgentTask(e.target.value)}
              placeholder="Enter a specific task for the selected agent..."
              className="w-full px-3 py-2 rounded-lg text-sm bg-transparent resize-none"
              style={{
                border: '1px solid var(--color-border)',
                color: 'var(--color-text)',
                minHeight: '60px',
              }}
              disabled={sendingTask}
            />
          </div>

          <button
            onClick={handleSendTask}
            disabled={sendingTask || !selectedAgent || !agentTask.trim()}
            className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium cursor-pointer transition-opacity"
            style={{
              background: 'var(--color-accent)',
              color: 'var(--color-on-accent)',
              opacity: sendingTask || !selectedAgent || !agentTask.trim() ? 0.6 : 1,
            }}
          >
            {sendingTask ? (
              <>
                <Loader2 size={14} className="animate-spin" /> Sending...
              </>
            ) : (
              <>
                <Send size={14} /> Send Task
              </>
            )}
          </button>

          {taskResult && (
            <div
              className="p-3 rounded-lg border"
              style={{
                background:
                  taskResult.status === 'pending'
                    ? 'var(--color-accent)20'
                    : taskResult.status === 'completed'
                      ? 'var(--color-success)20'
                      : 'var(--color-error)20',
                borderColor:
                  taskResult.status === 'pending'
                    ? 'var(--color-accent)'
                    : taskResult.status === 'completed'
                      ? 'var(--color-success)'
                      : 'var(--color-error)',
              }}
            >
              <div
                className="text-xs font-semibold mb-1 flex items-center gap-2"
                style={{
                  color:
                    taskResult.status === 'pending'
                      ? 'var(--color-accent)'
                      : taskResult.status === 'completed'
                        ? 'var(--color-success)'
                        : 'var(--color-error)',
                }}
              >
                {taskResult.status === 'pending' && <Loader2 size={12} className="animate-spin" />}
                {taskResult.status === 'completed' && <CheckCircle2 size={12} />}
                {taskResult.status === 'error' && <AlertCircle size={12} />}
                {taskResult.status.toUpperCase()}
              </div>
              <div
                className="text-sm"
                style={{
                  color:
                    taskResult.status === 'error'
                      ? 'var(--color-error)'
                      : 'var(--color-text-secondary)',
                }}
              >
                {taskResult.message || taskResult.error}
              </div>
              {taskResult.task_id && (
                <div className="text-xs mt-1" style={{ color: 'var(--color-text-tertiary)' }}>
                  Task ID: {taskResult.task_id}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
