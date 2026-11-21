import axios from 'axios'
import type {
  AgentProfile,
  AgentResult,
  ApiResponse,
  Conversation,
  EvaluationSummary,
  EvaluationTask,
  ExecutionEvent,
  MemoryStats,
  ModelComparison,
  Message,
  TaskPlan,
  ToolDescriptor,
  WorkflowRunResult,
} from '../types'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) {
    throw new Error(response.message)
  }
  return response.data
}

export async function listConversations(): Promise<Conversation[]> {
  const { data } = await http.get<ApiResponse<Conversation[]>>('/chat/conversations')
  return unwrap(data)
}

export async function createConversation(title?: string): Promise<Conversation> {
  const { data } = await http.post<ApiResponse<Conversation>>('/chat/conversations', { title })
  return unwrap(data)
}

export async function listMessages(conversationId: string): Promise<Message[]> {
  const { data } = await http.get<ApiResponse<Message[]>>(
    `/chat/conversations/${conversationId}/messages`,
  )
  return unwrap(data)
}

export async function listTools(): Promise<ToolDescriptor[]> {
  const { data } = await http.get<ApiResponse<ToolDescriptor[]>>('/tools')
  return unwrap(data)
}

 
export async function refreshMcpTools(): Promise<ToolDescriptor[]> {
  const { data } = await http.post<ApiResponse<ToolDescriptor[]>>('/mcp/refresh')
  return unwrap(data)
}

export async function listAgents(): Promise<AgentProfile[]> {
  const { data } = await http.get<ApiResponse<AgentProfile[]>>('/agents')
  return unwrap(data)
}

 
export async function getMemoryStats(conversationId: string): Promise<MemoryStats> {
  const { data } = await http.get<ApiResponse<MemoryStats>>(`/memory/${conversationId}/stats`)
  return unwrap(data)
}

export async function compactMemory(conversationId: string): Promise<MemoryStats> {
  const { data } = await http.post<ApiResponse<MemoryStats>>(`/memory/${conversationId}/compact`)
  return unwrap(data)
}

export async function generatePlan(payload: StreamPayload): Promise<TaskPlan> {
  const { data } = await http.post<ApiResponse<TaskPlan>>('/plans', payload)
  return unwrap(data)
}

export async function runAgent(payload: StreamPayload): Promise<AgentResult> {
  const { data } = await http.post<ApiResponse<AgentResult>>('/agent/runs', payload)
  return unwrap(data)
}

export async function cancelAgentRun(runId: string): Promise<void> {
  const { data } = await http.post<ApiResponse<unknown>>(`/agent/runs/${runId}/cancel`)
  unwrap(data)
}

export async function runDemoWorkflow(): Promise<WorkflowRunResult> {
  const { data } = await http.post<ApiResponse<WorkflowRunResult>>('/workflows/demo/run')
  return unwrap(data)
}

 
export async function resumeWorkflow(runId: string): Promise<WorkflowRunResult> {
  const { data } = await http.post<ApiResponse<WorkflowRunResult>>(
    `/workflows/runs/${runId}/resume`,
  )
  return unwrap(data)
}

 
export async function getTraceByRun(runId: string): Promise<ExecutionEvent[]> {
  const { data } = await http.get<ApiResponse<ExecutionEvent[]>>(
    `/trace/runs/${runId}/events`,
  )
  return unwrap(data)
}

export async function getTraceByConversation(
  conversationId: string,
): Promise<ExecutionEvent[]> {
  const { data } = await http.get<ApiResponse<ExecutionEvent[]>>(
    `/trace/conversations/${conversationId}/events`,
  )
  return unwrap(data)
}

export interface StreamPayload {
  conversationId?: string
  message: string
  provider?: string
}

async function readSseStream(
  response: Response,
  onEvent: (event: ExecutionEvent) => void,
): Promise<void> {
  if (!response.body) throw new Error('响应中没有可读的流')
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const consumeFrame = (frame: string): void => {
    const dataLine = frame.split('\n').find((line) => line.startsWith('data:'))
    if (!dataLine) return
    const json = dataLine.slice(5).trim()
    if (!json || json === '[DONE]') return
    try {
      onEvent(JSON.parse(json) as ExecutionEvent)
    } catch (e) {
      console.warn('忽略无法解析的 SSE 帧', json, e)
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
    let separatorIndex = buffer.indexOf('\n\n')
    while (separatorIndex !== -1) {
      consumeFrame(buffer.slice(0, separatorIndex))
      buffer = buffer.slice(separatorIndex + 2)
      separatorIndex = buffer.indexOf('\n\n')
    }
  }
  if (buffer.trim()) consumeFrame(buffer)
}

async function openStream(
  url: string,
  payload: StreamPayload | undefined,
  onEvent: (event: ExecutionEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: payload ? JSON.stringify(payload) : undefined,
    signal,
  })
  if (!response.ok) {
    throw new Error(`SSE 请求失败: HTTP ${response.status}`)
  }
  await readSseStream(response, onEvent)
}

export function streamChat(
  payload: StreamPayload,
  onEvent: (event: ExecutionEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  return openStream('/api/chat/stream', payload, onEvent, signal)
}

export function streamAgentRun(
  payload: StreamPayload,
  onEvent: (event: ExecutionEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  return openStream('/api/agent/runs/stream', payload, onEvent, signal)
}

export function streamPlan(
  payload: StreamPayload,
  onEvent: (event: ExecutionEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  return openStream('/api/plans/stream', payload, onEvent, signal)
}

export function streamDemoWorkflow(
  onEvent: (event: ExecutionEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  return openStream('/api/workflows/demo/stream', undefined, onEvent, signal)
}

 
export async function getEvaluationSummary(): Promise<EvaluationSummary> {
  const { data } = await http.get<ApiResponse<EvaluationSummary>>('/evaluation/summary')
  return unwrap(data)
}

export async function getModelComparison(): Promise<ModelComparison[]> {
  const { data } = await http.get<ApiResponse<ModelComparison[]>>('/evaluation/models')
  return unwrap(data)
}

export async function getEvaluationTasks(): Promise<EvaluationTask[]> {
  const { data } = await http.get<ApiResponse<EvaluationTask[]>>('/evaluation/tasks')
  return unwrap(data)
}
