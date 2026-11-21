export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface Conversation {
  id: string
  title: string
  status: string
  modelProvider?: string
  lastMessageAt?: string
  createdAt: string
}

export interface Message {
  id: string
  conversationId: string
  runId?: string
  role: string
  content: string
  contentType: string
  modelName?: string
  promptTokens?: number
  completionTokens?: number
  latencyMs?: number
  createdAt: string
}

 
export interface ExecutionEvent {
  eventId: string
  event: string
  runId: string
  conversationId: string
  agentId?: string
  nodeId?: string
  toolCallId?: string
  level: string
  message?: string
  payload?: Record<string, unknown>
  timestamp: number
}

export interface AgentResult {
  runId: string
  state: string
  terminationReason: string
  finalAnswer: string | null
  rounds: number
  latencyMs: number
  totalTokens: number
  errorMessage: string | null
}

export interface ToolDescriptor {
  name: string
  description: string
  category: string
  jsonSchema: Record<string, unknown>
  requiredPermissions: string[]
  timeoutMs: number
}

export interface PlanTask {
  id: string
  title: string
  description?: string
  dependsOn: string[]
  agentRole?: string | null
}

export interface TaskPlan {
  goal: string
  tasks: PlanTask[]
}

export interface AgentProfile {
  id: string
  name: string
  role: string
  systemPrompt: string
  allowedTools: string[]
  modelProvider?: string | null
  modelName?: string | null
  maxRounds: number
  timeoutMs: number
  enabled: boolean
}

export interface AgentStatus {
  taskId: string
  role: string
  state: string
  latencyMs?: number | null
}

export interface WorkflowNodeOutcome {
  nodeKey: string
  level: number
  state: string
  output?: string | null
  error?: string | null
  latencyMs: number
}

export interface WorkflowRunResult {
  runId: string
  workflowId: string
  status: string
  outcomes: WorkflowNodeOutcome[]
  totalNodes: number
  succeededNodes: number
  latencyMs: number
}

export interface WorkflowNodeStatus {
  nodeKey: string
  level: number
  state: string
  latencyMs?: number
}

 
export interface MemoryStats {
  historyCount: number
  retainedCount: number
  compactedCount: number
  contextChars: number
  summaryPresent: boolean
  reusableResults: number
}

 
export interface EvaluationSummary {
  totalRuns: number
  succeededRuns: number
  successRate: number
  averageLatencyMs: number
  totalTokens: number
  totalToolCalls: number
  toolSuccessRate: number
  failureReasons: Record<string, number>
}

export interface ModelComparison {
  modelName: string
  runs: number
  successRate: number
  averageLatencyMs: number
  totalTokens: number
}

export interface EvaluationTask {
  id: string
  name: string
  category: string
  prompt: string
  expectedKeywords: string[]
  expectedTool?: string | null
}

 
export type RunMode = 'chat' | 'agent' | 'plan'
