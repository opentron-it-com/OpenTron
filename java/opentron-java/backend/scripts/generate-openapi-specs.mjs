import fs from "node:fs";
import path from "node:path";

const outDir = path.resolve("java/opentron-java/backend");

const ref = (name) => ({ $ref: `#/components/schemas/${name}` });

const jsonResponse = (description = "OK", schema = ref("GenericObject")) => ({
  description,
  content: {
    "application/json": {
      schema
    }
  }
});

const sseResponse = (description = "Server-sent events stream") => ({
  description,
  content: {
    "text/event-stream": {
      schema: { type: "string" }
    }
  }
});

const htmlResponse = (description = "HTML response") => ({
  description,
  content: {
    "text/html": {
      schema: { type: "string" }
    }
  }
});

const binaryResponse = (description = "Binary file") => ({
  description,
  content: {
    "application/octet-stream": {
      schema: { type: "string", format: "binary" }
    }
  }
});

const emptyBody = () => ({ type: "object", additionalProperties: true });

const jsonBody = (required = true, schema = emptyBody()) => ({
  required,
  content: {
    "application/json": {
      schema
    }
  }
});

const multipartBody = (required = true) => ({
  required,
  content: {
    "multipart/form-data": {
      schema: {
        type: "object",
        properties: {
          file: { type: "string", format: "binary" }
        },
        required: ["file"]
      }
    }
  }
});

const p = (name, description) => ({
  name,
  in: "path",
  required: true,
  schema: { type: "string" },
  description
});

const q = (name, description, required = false) => ({
  name,
  in: "query",
  required,
  schema: { type: "string" },
  description
});

const qi = (name, description, required = false, defVal) => ({
  name,
  in: "query",
  required,
  schema: {
    type: "integer",
    ...(defVal !== undefined ? { default: defVal } : {})
  },
  description
});

const spec = {
  openapi: "3.0.3",
  info: {
    title: "OpenTron Backend REST API",
    version: "1.0.0",
    description: "Generated OpenAPI specification for all REST endpoints in the OpenTron Java backend controllers."
  },
  servers: [
    { url: "http://localhost:8080", description: "Local development" }
  ],
  tags: [
    { name: "Health" },
    { name: "Agent Templates" },
    { name: "Agents" },
    { name: "Channels" },
    { name: "Chat" },
    { name: "Compose" },
    { name: "Connectors" },
    { name: "Demo" },
    { name: "Management" },
    { name: "Managed Agents" },
    { name: "Agent Channels" },
    { name: "Memory" },
    { name: "Models" },
    { name: "Orchestration" },
    { name: "Projects" },
    { name: "Project Generation" },
    { name: "Download" },
    { name: "Savings" },
    { name: "Screenshot" },
    { name: "Speech" },
    { name: "Telemetry" },
    { name: "Tools" },
    { name: "Traces" },
    { name: "Workflow" },
    { name: "Jarvis Voice" },
    { name: "Forwarding" }
  ],
  paths: {
    "/health": {
      get: {
        tags: ["Health"],
        summary: "Health check",
        operationId: "health",
        responses: { "200": jsonResponse("Service health") }
      }
    },
    "/actuator/health": {
      get: {
        tags: ["Health"],
        summary: "Actuator health",
        operationId: "actuatorHealth",
        responses: { "200": jsonResponse("Actuator health") }
      }
    },
    "/v1/agent-templates": {
      get: {
        tags: ["Agent Templates"],
        summary: "List agent templates",
        operationId: "listAgentTemplates",
        responses: { "200": jsonResponse("Agent templates list", { type: "array", items: ref("GenericObject") }) }
      }
    },
    "/v1/agent-templates/{id}": {
      get: {
        tags: ["Agent Templates"],
        summary: "Get agent template",
        operationId: "getAgentTemplate",
        parameters: [p("id", "Template identifier")],
        responses: { "200": jsonResponse("Agent template", ref("GenericObject")) }
      }
    },
    "/v1/agents/coordinate": {
      post: {
        tags: ["Agents"],
        summary: "Coordinate multi-agent execution",
        operationId: "coordinateAgents",
        requestBody: jsonBody(true),
        responses: {
          "200": {
            description: "JSON or SSE response depending on stream flag",
            content: {
              "application/json": { schema: ref("GenericObject") },
              "text/event-stream": { schema: { type: "string" } }
            }
          }
        }
      }
    },
    "/v1/agents/status": {
      get: {
        tags: ["Agents"],
        summary: "Get all agent statuses",
        operationId: "getAgentsStatus",
        responses: { "200": jsonResponse("Agent statuses") }
      }
    },
    "/v1/agents/task": {
      post: {
        tags: ["Agents"],
        summary: "Submit async task",
        operationId: "submitAgentTask",
        requestBody: jsonBody(true),
        responses: { "202": jsonResponse("Task accepted") }
      }
    },
    "/v1/agents/task/{taskId}": {
      get: {
        tags: ["Agents"],
        summary: "Poll async task result",
        operationId: "getAgentTask",
        parameters: [p("taskId", "Task identifier")],
        responses: { "200": jsonResponse("Task status or result") }
      }
    },
    "/v1/channels": {
      get: {
        tags: ["Channels"],
        summary: "List channels",
        operationId: "listChannels",
        responses: { "200": jsonResponse("Channels list", { type: "array", items: ref("GenericObject") }) }
      }
    },
    "/v1/channels/{channelId}/test": {
      post: {
        tags: ["Channels"],
        summary: "Test channel connectivity",
        operationId: "testChannel",
        parameters: [p("channelId", "Channel identifier")],
        responses: { "200": jsonResponse("Connectivity test result") }
      }
    },
    "/v1/channels/{channelId}/disconnect": {
      post: {
        tags: ["Channels"],
        summary: "Disconnect channel",
        operationId: "disconnectChannel",
        parameters: [p("channelId", "Channel identifier")],
        responses: { "200": jsonResponse("Disconnect result") }
      }
    },
    "/v1/chat/completions": {
      post: {
        tags: ["Chat"],
        summary: "Chat completions",
        operationId: "chatCompletions",
        requestBody: jsonBody(true),
        responses: {
          "200": {
            description: "Chat completion result or stream",
            content: {
              "application/json": { schema: ref("GenericObject") },
              "text/event-stream": { schema: { type: "string" } }
            }
          }
        }
      }
    },
    "/v1/compose": {
      get: {
        tags: ["Compose"],
        summary: "List compositions",
        operationId: "listCompositions",
        responses: { "200": jsonResponse("Composition list", { type: "array", items: ref("GenericObject") }) }
      }
    },
    "/v1/compose/{name}": {
      get: {
        tags: ["Compose"],
        summary: "Get composition",
        operationId: "getComposition",
        parameters: [p("name", "Composition name")],
        responses: { "200": jsonResponse("Composition details") }
      }
    },
    "/v1/compose/run": {
      post: {
        tags: ["Compose"],
        summary: "Run composition",
        operationId: "runComposition",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Run result") }
      }
    },
    "/v1/compose/bench": {
      post: {
        tags: ["Compose"],
        summary: "Benchmark composition",
        operationId: "benchComposition",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Benchmark result") }
      }
    },
    "/v1/compose/deploy": {
      post: {
        tags: ["Compose"],
        summary: "Deploy composition",
        operationId: "deployComposition",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Deploy result") }
      }
    },
    "/v1/compose/stop": {
      post: {
        tags: ["Compose"],
        summary: "Stop composition",
        operationId: "stopComposition",
        requestBody: jsonBody(false),
        responses: { "200": jsonResponse("Stop result") }
      }
    },
    "/v1/compose/status": {
      get: {
        tags: ["Compose"],
        summary: "Composition status",
        operationId: "compositionStatus",
        responses: { "200": jsonResponse("Current status") }
      }
    },
    "/v1/connectors": {
      get: {
        tags: ["Connectors"],
        summary: "List connectors",
        operationId: "listConnectors",
        responses: { "200": jsonResponse("Connectors list") }
      }
    },
    "/v1/connectors/{connectorId}": {
      get: {
        tags: ["Connectors"],
        summary: "Get connector",
        operationId: "getConnector",
        parameters: [p("connectorId", "Connector identifier")],
        responses: { "200": jsonResponse("Connector details") }
      }
    },
    "/v1/connectors/{connectorId}/connect": {
      post: {
        tags: ["Connectors"],
        summary: "Connect connector",
        operationId: "connectConnector",
        parameters: [p("connectorId", "Connector identifier")],
        requestBody: jsonBody(false),
        responses: { "200": jsonResponse("Connection result") }
      }
    },
    "/v1/connectors/{connectorId}/disconnect": {
      post: {
        tags: ["Connectors"],
        summary: "Disconnect connector",
        operationId: "disconnectConnector",
        parameters: [p("connectorId", "Connector identifier")],
        responses: { "200": jsonResponse("Disconnect result") }
      }
    },
    "/v1/connectors/{connectorId}/sync": {
      get: {
        tags: ["Connectors"],
        summary: "Get connector sync status",
        operationId: "getConnectorSync",
        parameters: [p("connectorId", "Connector identifier")],
        responses: { "200": jsonResponse("Sync status") }
      },
      post: {
        tags: ["Connectors"],
        summary: "Trigger connector sync",
        operationId: "triggerConnectorSync",
        parameters: [p("connectorId", "Connector identifier")],
        responses: { "200": jsonResponse("Sync trigger result") }
      }
    },
    "/v1/connectors/{connectorId}/oauth/start": {
      get: {
        tags: ["Connectors"],
        summary: "Start OAuth flow",
        operationId: "startConnectorOAuth",
        parameters: [p("connectorId", "Connector identifier")],
        responses: { "200": htmlResponse("OAuth start page") }
      }
    },
    "/v1/connectors/{connectorId}/oauth/callback": {
      get: {
        tags: ["Connectors"],
        summary: "OAuth callback",
        operationId: "connectorOAuthCallback",
        parameters: [
          p("connectorId", "Connector identifier"),
          q("code", "OAuth authorization code"),
          q("state", "OAuth state"),
          q("error", "OAuth error code")
        ],
        responses: { "200": htmlResponse("OAuth callback response") }
      }
    },
    "/v1/demo/generate-react-auth": {
      post: {
        tags: ["Demo"],
        summary: "Generate demo React auth project",
        operationId: "generateReactAuthDemo",
        responses: { "200": jsonResponse("Generated project payload") }
      }
    },
    "/v1/recommended-model": {
      get: {
        tags: ["Management"],
        summary: "Get recommended model",
        operationId: "recommendedModel",
        responses: { "200": jsonResponse("Recommended model") }
      }
    },
    "/v1/approvals/pending": {
      get: {
        tags: ["Management"],
        summary: "List pending approvals",
        operationId: "pendingApprovals",
        responses: { "200": jsonResponse("Pending approvals") }
      }
    },
    "/v1/info": {
      get: {
        tags: ["Management"],
        summary: "Get backend info",
        operationId: "backendInfo",
        responses: { "200": jsonResponse("Service info") }
      }
    },
    "/v1/analytics/identity": {
      get: {
        tags: ["Management"],
        summary: "Get analytics identity",
        operationId: "analyticsIdentity",
        responses: { "200": jsonResponse("Analytics identity configuration") }
      }
    },
    "/v1/tools/{toolName}/credentials": {
      post: {
        tags: ["Management"],
        summary: "Save tool credentials",
        operationId: "saveToolCredentials",
        parameters: [p("toolName", "Tool name")],
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Credential save result") }
      }
    },
    "/v1/managed-agents": {
      get: {
        tags: ["Managed Agents"],
        summary: "List managed agents",
        operationId: "listManagedAgents",
        responses: { "200": jsonResponse("Managed agents", { type: "array", items: ref("GenericObject") }) }
      },
      post: {
        tags: ["Managed Agents"],
        summary: "Create managed agent",
        operationId: "createManagedAgent",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Created managed agent") }
      }
    },
    "/v1/managed-agents/{agentId}": {
      get: {
        tags: ["Managed Agents"],
        summary: "Get managed agent",
        operationId: "getManagedAgent",
        parameters: [p("agentId", "Agent identifier")],
        responses: { "200": jsonResponse("Managed agent details") }
      },
      patch: {
        tags: ["Managed Agents"],
        summary: "Update managed agent",
        operationId: "updateManagedAgent",
        parameters: [p("agentId", "Agent identifier")],
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Updated managed agent") }
      },
      delete: {
        tags: ["Managed Agents"],
        summary: "Delete managed agent",
        operationId: "deleteManagedAgent",
        parameters: [p("agentId", "Agent identifier")],
        responses: { "200": jsonResponse("Delete result") }
      }
    },
    "/v1/managed-agents/{agentId}/run": {
      post: {
        tags: ["Managed Agents"],
        summary: "Run managed agent",
        operationId: "runManagedAgent",
        parameters: [p("agentId", "Agent identifier")],
        requestBody: jsonBody(false),
        responses: { "202": jsonResponse("Run accepted") }
      }
    },
    "/v1/managed-agents/{agentId}/pause": {
      post: {
        tags: ["Managed Agents"],
        summary: "Pause managed agent",
        operationId: "pauseManagedAgent",
        parameters: [p("agentId", "Agent identifier")],
        responses: { "200": jsonResponse("Pause result") }
      }
    },
    "/v1/managed-agents/{agentId}/resume": {
      post: {
        tags: ["Managed Agents"],
        summary: "Resume managed agent",
        operationId: "resumeManagedAgent",
        parameters: [p("agentId", "Agent identifier")],
        responses: { "200": jsonResponse("Resume result") }
      }
    },
    "/v1/managed-agents/{agentId}/recover": {
      post: {
        tags: ["Managed Agents"],
        summary: "Recover managed agent",
        operationId: "recoverManagedAgent",
        parameters: [p("agentId", "Agent identifier")],
        responses: { "200": jsonResponse("Recover result") }
      }
    },
    "/v1/managed-agents/{agentId}/ask": {
      post: {
        tags: ["Managed Agents"],
        summary: "Ask managed agent",
        operationId: "askManagedAgent",
        parameters: [p("agentId", "Agent identifier")],
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Answer payload") }
      }
    },
    "/v1/managed-agents/{agentId}/state": {
      get: {
        tags: ["Managed Agents"],
        summary: "Get agent state",
        operationId: "managedAgentState",
        parameters: [p("agentId", "Agent identifier")],
        responses: { "200": jsonResponse("Agent state") }
      }
    },
    "/v1/managed-agents/{agentId}/messages": {
      get: {
        tags: ["Managed Agents"],
        summary: "Get agent messages",
        operationId: "managedAgentMessages",
        parameters: [p("agentId", "Agent identifier")],
        responses: { "200": jsonResponse("Agent messages", { type: "array", items: ref("GenericObject") }) }
      }
    },
    "/v1/managed-agents/{agentId}/tasks": {
      get: {
        tags: ["Managed Agents"],
        summary: "Get agent tasks",
        operationId: "managedAgentTasks",
        parameters: [p("agentId", "Agent identifier")],
        responses: { "200": jsonResponse("Agent tasks", { type: "array", items: ref("GenericObject") }) }
      }
    },
    "/v1/managed-agents/{agentId}/learning/trigger": {
      post: {
        tags: ["Managed Agents"],
        summary: "Trigger agent learning",
        operationId: "triggerManagedAgentLearning",
        parameters: [p("agentId", "Agent identifier")],
        responses: { "200": jsonResponse("Learning trigger result") }
      }
    },
    "/v1/managed-agents/{agentId}/learning": {
      get: {
        tags: ["Managed Agents"],
        summary: "Get learning status",
        operationId: "managedAgentLearning",
        parameters: [p("agentId", "Agent identifier")],
        responses: { "200": jsonResponse("Learning status") }
      }
    },
    "/v1/managed-agents/{agentId}/traces": {
      get: {
        tags: ["Managed Agents"],
        summary: "Get agent traces",
        operationId: "managedAgentTraces",
        parameters: [p("agentId", "Agent identifier")],
        responses: { "200": jsonResponse("Agent traces", { type: "array", items: ref("GenericObject") }) }
      }
    },
    "/v1/managed-agents/{agentId}/channels": {
      get: {
        tags: ["Agent Channels"],
        summary: "List agent channel bindings",
        operationId: "listAgentChannelBindings",
        parameters: [p("agentId", "Agent identifier")],
        responses: { "200": jsonResponse("Channel bindings", { type: "array", items: ref("GenericObject") }) }
      },
      post: {
        tags: ["Agent Channels"],
        summary: "Create agent channel binding",
        operationId: "createAgentChannelBinding",
        parameters: [p("agentId", "Agent identifier")],
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Created channel binding") }
      }
    },
    "/v1/managed-agents/{agentId}/channels/bind": {
      post: {
        tags: ["Agent Channels"],
        summary: "Bind channel alias",
        operationId: "bindAgentChannel",
        parameters: [p("agentId", "Agent identifier")],
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Bind result") }
      }
    },
    "/v1/managed-agents/{agentId}/channels/{bindingId}": {
      delete: {
        tags: ["Agent Channels"],
        summary: "Delete channel binding",
        operationId: "deleteAgentChannelBinding",
        parameters: [p("agentId", "Agent identifier"), p("bindingId", "Binding identifier")],
        responses: { "200": jsonResponse("Delete binding result") }
      }
    },
    "/v1/memory/stats": {
      get: {
        tags: ["Memory"],
        summary: "Memory stats",
        operationId: "memoryStats",
        responses: { "200": jsonResponse("Memory statistics") }
      }
    },
    "/v1/memory/config": {
      get: {
        tags: ["Memory"],
        summary: "Memory config",
        operationId: "memoryConfig",
        responses: { "200": jsonResponse("Memory configuration") }
      }
    },
    "/v1/memory/store": {
      post: {
        tags: ["Memory"],
        summary: "Store memory item",
        operationId: "storeMemory",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Store result") }
      }
    },
    "/v1/memory/search": {
      post: {
        tags: ["Memory"],
        summary: "Search memory",
        operationId: "searchMemory",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Search results", { type: "array", items: ref("GenericObject") }) }
      }
    },
    "/v1/memory/index": {
      post: {
        tags: ["Memory"],
        summary: "Index file into memory",
        operationId: "indexMemoryFile",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Indexing result") }
      }
    },
    "/v1/memory/agent/{agentId}": {
      get: {
        tags: ["Memory"],
        summary: "Get agent memory",
        operationId: "agentMemory",
        parameters: [p("agentId", "Agent identifier"), qi("limit", "Maximum records", false, 50)],
        responses: { "200": jsonResponse("Agent memory", { type: "array", items: ref("GenericObject") }) }
      }
    },
    "/v1/memory/stats/detailed": {
      get: {
        tags: ["Memory"],
        summary: "Detailed memory stats",
        operationId: "memoryStatsDetailed",
        responses: { "200": jsonResponse("Detailed memory statistics") }
      }
    },
    "/v1/models": {
      get: {
        tags: ["Models"],
        summary: "List models",
        operationId: "listModels",
        responses: { "200": jsonResponse("Model list") }
      }
    },
    "/v1/models/pull": {
      post: {
        tags: ["Models"],
        summary: "Pull model",
        operationId: "pullModel",
        requestBody: jsonBody(true),
        responses: { "202": jsonResponse("Pull job accepted") }
      }
    },
    "/v1/models/pull/{jobId}": {
      get: {
        tags: ["Models"],
        summary: "Get pull job status",
        operationId: "getPullJob",
        parameters: [p("jobId", "Pull job identifier")],
        responses: { "200": jsonResponse("Pull job status") }
      }
    },
    "/v1/models/pull/{jobId}/events": {
      get: {
        tags: ["Models"],
        summary: "Stream pull job events",
        operationId: "pullJobEvents",
        parameters: [p("jobId", "Pull job identifier")],
        responses: { "200": sseResponse("Pull job event stream") }
      }
    },
    "/v1/orchestrate/task": {
      post: {
        tags: ["Orchestration"],
        summary: "Orchestrate one task",
        operationId: "orchestrateTask",
        parameters: [q("task", "Task description", true), q("context", "Optional task context")],
        responses: { "200": jsonResponse("Orchestration result") }
      }
    },
    "/v1/orchestrate/batch": {
      post: {
        tags: ["Orchestration"],
        summary: "Orchestrate batch tasks",
        operationId: "orchestrateBatch",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Batch orchestration result") }
      }
    },
    "/v1/orchestrate/agents": {
      get: {
        tags: ["Orchestration"],
        summary: "List orchestration agents",
        operationId: "orchestrationAgents",
        responses: { "200": jsonResponse("Available agents") }
      }
    },
    "/v1/orchestrate/domains": {
      get: {
        tags: ["Orchestration"],
        summary: "List orchestration domains",
        operationId: "orchestrationDomains",
        responses: { "200": jsonResponse("Available domains") }
      }
    },
    "/v1/download/react-auth": {
      get: {
        tags: ["Download"],
        summary: "Download React auth ZIP",
        operationId: "downloadReactAuth",
        responses: { "200": binaryResponse("ZIP archive") }
      }
    },
    "/v1/download/spring-api": {
      get: {
        tags: ["Download"],
        summary: "Download Spring API ZIP",
        operationId: "downloadSpringApi",
        responses: { "200": binaryResponse("ZIP archive") }
      }
    },
    "/v1/generate/project": {
      post: {
        tags: ["Project Generation"],
        summary: "Generate complete project",
        operationId: "generateProject",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Generation result") }
      }
    },
    "/v1/generate/templates": {
      get: {
        tags: ["Project Generation"],
        summary: "List templates",
        operationId: "listProjectTemplates",
        responses: { "200": jsonResponse("Template list") }
      }
    },
    "/v1/generate/from-template": {
      post: {
        tags: ["Project Generation"],
        summary: "Generate from template",
        operationId: "generateFromTemplate",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Generated project result") }
      }
    },
    "/v1/generate/frameworks": {
      get: {
        tags: ["Project Generation"],
        summary: "List frameworks",
        operationId: "listFrameworks",
        responses: { "200": jsonResponse("Framework list") }
      }
    },
    "/v1/generate/code": {
      post: {
        tags: ["Project Generation"],
        summary: "Generate code snippet",
        operationId: "generateCode",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Generated code payload") }
      }
    },
    "/v1/projects/create": {
      post: {
        tags: ["Projects"],
        summary: "Create project",
        operationId: "createProject",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Created project") }
      }
    },
    "/v1/projects": {
      get: {
        tags: ["Projects"],
        summary: "List projects",
        operationId: "listProjects",
        parameters: [qi("limit", "Maximum records", false, 50)],
        responses: { "200": jsonResponse("Projects", { type: "array", items: ref("GenericObject") }) }
      }
    },
    "/v1/projects/{projectId}": {
      get: {
        tags: ["Projects"],
        summary: "Get project",
        operationId: "getProject",
        parameters: [p("projectId", "Project identifier")],
        responses: { "200": jsonResponse("Project detail") }
      }
    },
    "/v1/savings": {
      get: {
        tags: ["Savings"],
        summary: "Cost savings",
        operationId: "costSavings",
        responses: { "200": jsonResponse("Savings calculation") }
      }
    },
    "/v1/analyze-screenshot": {
      post: {
        tags: ["Screenshot"],
        summary: "Analyze screenshot",
        operationId: "analyzeScreenshot",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Screenshot analysis result") }
      }
    },
    "/v1/speech/health": {
      get: {
        tags: ["Speech"],
        summary: "Speech health",
        operationId: "speechHealth",
        responses: { "200": jsonResponse("Speech service health") }
      }
    },
    "/v1/speech/transcribe": {
      post: {
        tags: ["Speech"],
        summary: "Transcribe audio",
        operationId: "transcribeAudio",
        requestBody: multipartBody(true),
        responses: { "200": jsonResponse("Transcription result") }
      }
    },
    "/v1/speech/synthesize": {
      post: {
        tags: ["Speech"],
        summary: "Synthesize speech",
        operationId: "synthesizeSpeech",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Synthesis result") }
      }
    },
    "/v1/speech/voices": {
      get: {
        tags: ["Speech"],
        summary: "List speech voices",
        operationId: "listSpeechVoices",
        responses: { "200": jsonResponse("Voice list") }
      }
    },
    "/v1/telemetry/energy": {
      get: {
        tags: ["Telemetry"],
        summary: "Energy telemetry",
        operationId: "telemetryEnergy",
        responses: { "200": jsonResponse("Energy metrics") }
      }
    },
    "/v1/telemetry/stats": {
      get: {
        tags: ["Telemetry"],
        summary: "Telemetry stats",
        operationId: "telemetryStats",
        responses: { "200": jsonResponse("Statistics") }
      }
    },
    "/v1/telemetry/track": {
      post: {
        tags: ["Telemetry"],
        summary: "Track telemetry event",
        operationId: "telemetryTrack",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Tracking result") }
      }
    },
    "/v1/telemetry/reset": {
      post: {
        tags: ["Telemetry"],
        summary: "Reset telemetry",
        operationId: "telemetryReset",
        responses: { "200": jsonResponse("Reset result") }
      }
    },
    "/v1/tools": {
      get: {
        tags: ["Tools"],
        summary: "List tools",
        operationId: "listTools",
        responses: { "200": jsonResponse("Tool list") }
      }
    },
    "/v1/tools/{name}": {
      get: {
        tags: ["Tools"],
        summary: "Get tool",
        operationId: "getTool",
        parameters: [p("name", "Tool name")],
        responses: { "200": jsonResponse("Tool details") }
      }
    },
    "/v1/traces": {
      get: {
        tags: ["Traces"],
        summary: "List traces",
        operationId: "listTraces",
        parameters: [qi("limit", "Maximum records", false, 50)],
        responses: { "200": jsonResponse("Trace list", { type: "array", items: ref("GenericObject") }) }
      }
    },
    "/v1/traces/{traceId}": {
      get: {
        tags: ["Traces"],
        summary: "Get trace",
        operationId: "getTrace",
        parameters: [p("traceId", "Trace identifier")],
        responses: { "200": jsonResponse("Trace details") }
      }
    },
    "/v1/traces/agent/{agentId}": {
      get: {
        tags: ["Traces"],
        summary: "Get traces by agent",
        operationId: "getAgentTraces",
        parameters: [p("agentId", "Agent identifier"), qi("limit", "Maximum records", false, 50)],
        responses: { "200": jsonResponse("Agent traces", { type: "array", items: ref("GenericObject") }) }
      }
    },
    "/v1/workflow": {
      get: {
        tags: ["Workflow"],
        summary: "List workflows",
        operationId: "listWorkflows",
        responses: { "200": jsonResponse("Workflow list", { type: "array", items: ref("GenericObject") }) }
      }
    },
    "/v1/workflow/run": {
      post: {
        tags: ["Workflow"],
        summary: "Run workflow",
        operationId: "runWorkflow",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Workflow run result") }
      }
    },
    "/v1/workflow/status": {
      get: {
        tags: ["Workflow"],
        summary: "Workflow status",
        operationId: "workflowStatus",
        responses: { "200": jsonResponse("Workflow status") }
      }
    },
    "/v1/jarvis/health": {
      get: {
        tags: ["Jarvis Voice"],
        summary: "Jarvis voice health",
        operationId: "jarvisHealth",
        responses: { "200": jsonResponse("Jarvis voice health") }
      }
    },
    "/v1/jarvis/speak": {
      post: {
        tags: ["Jarvis Voice"],
        summary: "Speak text with Jarvis voice",
        operationId: "jarvisSpeak",
        requestBody: jsonBody(true),
        responses: { "200": jsonResponse("Speech synthesis result") }
      }
    },
    "/v1/jarvis/voice-profile": {
      get: {
        tags: ["Jarvis Voice"],
        summary: "Get voice profile",
        operationId: "jarvisVoiceProfile",
        responses: { "200": jsonResponse("Voice profile") }
      }
    },
    "/v1/jarvis/batch-speak": {
      post: {
        tags: ["Jarvis Voice"],
        summary: "Batch speak",
        operationId: "jarvisBatchSpeak",
        requestBody: jsonBody(true, { type: "array", items: { type: "string" } }),
        responses: { "200": jsonResponse("Batch synthesis result") }
      }
    },
    "/v1/{proxyPath}": {
      get: {
        tags: ["Forwarding"],
        summary: "Forward GET to engine",
        operationId: "forwardGet",
        parameters: [p("proxyPath", "Forwarded path segment after /v1")],
        responses: { "200": jsonResponse("Forwarded response") }
      },
      post: {
        tags: ["Forwarding"],
        summary: "Forward POST to engine",
        operationId: "forwardPost",
        parameters: [p("proxyPath", "Forwarded path segment after /v1")],
        requestBody: jsonBody(false),
        responses: { "200": jsonResponse("Forwarded response") }
      },
      put: {
        tags: ["Forwarding"],
        summary: "Forward PUT to engine",
        operationId: "forwardPut",
        parameters: [p("proxyPath", "Forwarded path segment after /v1")],
        requestBody: jsonBody(false),
        responses: { "200": jsonResponse("Forwarded response") }
      },
      delete: {
        tags: ["Forwarding"],
        summary: "Forward DELETE to engine",
        operationId: "forwardDelete",
        parameters: [p("proxyPath", "Forwarded path segment after /v1")],
        responses: { "200": jsonResponse("Forwarded response") }
      },
      patch: {
        tags: ["Forwarding"],
        summary: "Forward PATCH to engine",
        operationId: "forwardPatch",
        parameters: [p("proxyPath", "Forwarded path segment after /v1")],
        requestBody: jsonBody(false),
        responses: { "200": jsonResponse("Forwarded response") }
      },
      options: {
        tags: ["Forwarding"],
        summary: "Forward OPTIONS to engine",
        operationId: "forwardOptions",
        parameters: [p("proxyPath", "Forwarded path segment after /v1")],
        responses: { "200": jsonResponse("Forwarded response") }
      }
    }
  },
  components: {
    schemas: {
      GenericObject: {
        type: "object",
        additionalProperties: true
      },
      ErrorResponse: {
        type: "object",
        properties: {
          error: { type: "string" },
          message: { type: "string" }
        },
        additionalProperties: true
      }
    }
  }
};

function yamlScalar(value) {
  if (value === null) return "null";
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  const s = String(value);
  if (s.length === 0) return "\"\"";
  if (/[:#\-?\[\]{}&,*!|>'\"%@`]/.test(s) || /^\s|\s$/.test(s) || /\n/.test(s)) {
    return JSON.stringify(s);
  }
  return s;
}

function toYaml(value, indent = 0) {
  const pad = "  ".repeat(indent);

  if (Array.isArray(value)) {
    if (value.length === 0) return "[]";
    return value
      .map((item) => {
        if (item && typeof item === "object") {
          const nested = toYaml(item, indent + 1);
          const lines = nested.split("\n");
          return `${pad}- ${lines[0].trimStart()}${lines.length > 1 ? `\n${lines.slice(1).join("\n")}` : ""}`;
        }
        return `${pad}- ${yamlScalar(item)}`;
      })
      .join("\n");
  }

  if (value && typeof value === "object") {
    const entries = Object.entries(value);
    if (entries.length === 0) return "{}";
    return entries
      .map(([key, val]) => {
        if (val && typeof val === "object") {
          const nested = toYaml(val, indent + 1);
          return `${pad}${key}:\n${nested}`;
        }
        return `${pad}${key}: ${yamlScalar(val)}`;
      })
      .join("\n");
  }

  return `${pad}${yamlScalar(value)}`;
}

const jsonPath = path.join(outDir, "openapi.json");
const yamlPath = path.join(outDir, "openapi.yaml");

fs.writeFileSync(jsonPath, JSON.stringify(spec, null, 2) + "\n", "utf8");
fs.writeFileSync(yamlPath, toYaml(spec) + "\n", "utf8");

console.log(`Generated ${jsonPath}`);
console.log(`Generated ${yamlPath}`);
