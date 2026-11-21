import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from '../api/chat'
import type {
  AgentProfile,
  Conversation,
  EvaluationSummary,
  ExecutionEvent,
  MemoryStats,
  ModelComparison,
  Message,
  PlanTask,
  RunMode,
  TaskPlan,
  ToolDescriptor,
  WorkflowNodeStatus,
} from '../types'

 
export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const currentId = ref('')
  const messages = ref<Message[]>([])
  const events = ref<ExecutionEvent[]>([])
  const tools = ref<ToolDescriptor[]>([])
  const agents = ref<AgentProfile[]>([])
  const currentPlan = ref<TaskPlan | null>(null)
  const workflowNodes = ref<WorkflowNodeStatus[]>([])
  const memoryStats = ref<MemoryStats | null>(null)
  const evalSummary = ref<EvaluationSummary | null>(null)
  const modelComparison = ref<ModelComparison[]>([])
  const streamingText = ref('')
  const streaming = ref(false)
  const error = ref('')
  const mode = ref<RunMode>('agent')
  const currentRunId = ref('')
  const lastWorkflowRunId = ref('')

  let controller: AbortController | null = null

  async function init(): Promise<void> {
    await loadConversations()
    await loadTools()
    await loadAgents()
    if (conversations.value.length > 0) {
      await selectConversation(conversations.value[0].id)
    }
  }

  async function loadConversations(): Promise<void> {
    try {
      conversations.value = await api.listConversations()
    } catch (e) {
      error.value = toMessage(e)
    }
  }

  async function loadTools(): Promise<void> {
    try {
      tools.value = await api.listTools()
    } catch (e) {
      error.value = toMessage(e)
    }
  }

  async function loadAgents(): Promise<void> {
    try {
      agents.value = await api.listAgents()
    } catch (e) {
      error.value = toMessage(e)
    }
  }

  async function refreshMemoryStats(): Promise<void> {
    if (!currentId.value) {
      memoryStats.value = null
      return
    }
    try {
      memoryStats.value = await api.getMemoryStats(currentId.value)
    } catch {
      
    }
  }

  async function compactMemory(): Promise<void> {
    if (!currentId.value) return
    try {
      memoryStats.value = await api.compactMemory(currentId.value)
    } catch (e) {
      error.value = toMessage(e)
    }
  }

  async function refreshMcpTools(): Promise<void> {
    try {
      await api.refreshMcpTools()
      await loadTools()
    } catch (e) {
      error.value = toMessage(e)
    }
  }

   
  async function loadTrace(): Promise<void> {
    try {
      const history = currentId.value
        ? await api.getTraceByConversation(currentId.value)
        : lastWorkflowRunId.value
          ? await api.getTraceByRun(lastWorkflowRunId.value)
          : []
      const known = new Set(events.value.map((item) => item.eventId))
      for (const item of history) {
        if (!known.has(item.eventId)) {
          events.value.push(item)
          known.add(item.eventId)
        }
      }
      events.value.sort((a, b) => a.timestamp - b.timestamp)
    } catch (e) {
      error.value = toMessage(e)
    }
  }

  async function selectConversation(id: string): Promise<void> {
    currentId.value = id
    streamingText.value = ''
    events.value = []
    currentPlan.value = null
    workflowNodes.value = []
    try {
      messages.value = await api.listMessages(id)
    } catch (e) {
      error.value = toMessage(e)
    }
    void refreshMemoryStats()
  }

  function newConversation(): void {
    currentId.value = ''
    messages.value = []
    events.value = []
    streamingText.value = ''
    currentRunId.value = ''
    currentPlan.value = null
    workflowNodes.value = []
    memoryStats.value = null
    error.value = ''
  }

  function clearError(): void {
    error.value = ''
  }

  async function send(text: string): Promise<void> {
    if (streaming.value || !text.trim()) return
    error.value = ''
    events.value = []
    streamingText.value = ''
    currentRunId.value = ''
    currentPlan.value = null
    workflowNodes.value = []

    messages.value.push({
      id: `local-${Date.now()}`,
      conversationId: currentId.value,
      role: 'USER',
      content: text,
      contentType: 'TEXT',
      createdAt: new Date().toISOString(),
    })

    streaming.value = true
    controller = new AbortController()
    const payload = { conversationId: currentId.value || undefined, message: text }
    const stream = mode.value === 'chat'
      ? api.streamChat
      : mode.value === 'agent'
        ? api.streamAgentRun
        : api.streamPlan
    try {
      await stream(payload, (event: ExecutionEvent) => {
        events.value.push(event)
        if (!currentId.value && event.conversationId) {
          currentId.value = event.conversationId
        }
        if (event.event === 'run_started') currentRunId.value = event.runId
        if (event.event === 'plan_created' && event.payload) {
          const tasks = Array.isArray(event.payload.tasks)
            ? (event.payload.tasks as PlanTask[]) : []
          currentPlan.value = { goal: String(event.payload.goal ?? ''), tasks }
        }
        if (event.event === 'message_delta') {
          const delta = event.payload?.delta
          if (typeof delta === 'string') streamingText.value += delta
        }
        if (event.event === 'run_failed') {
          const reason = event.payload?.error
          error.value = typeof reason === 'string' ? reason : '任务执行失败'
        }
      }, controller.signal)
    } catch (e) {
      if (!(e instanceof DOMException && e.name === 'AbortError')) {
        error.value = toMessage(e)
      }
    } finally {
      streaming.value = false
      controller = null
      await loadConversations()
      if (currentId.value) {
        try {
          messages.value = await api.listMessages(currentId.value)
        } catch (e) {
          error.value = toMessage(e)
        }
      }
      void refreshMemoryStats()
    }
  }

  async function runWorkflowDemo(): Promise<void> {
    if (streaming.value) return
    error.value = ''
    events.value = []
    workflowNodes.value = []
    streamingText.value = ''
    streaming.value = true
    controller = new AbortController()
    try {
      await api.streamDemoWorkflow((event: ExecutionEvent) => {
        events.value.push(event)
        const payload = event.payload
        if (event.event === 'run_started') lastWorkflowRunId.value = event.runId
        if (event.event === 'node_started' && payload) {
          workflowNodes.value.push({
            nodeKey: String(payload.nodeKey ?? ''),
            level: typeof payload.level === 'number' ? payload.level : 0,
            state: 'RUNNING',
          })
        }
        if (event.event === 'node_completed' && payload) {
          const nodeKey = String(payload.nodeKey ?? '')
          const node = workflowNodes.value.find((item) => item.nodeKey === nodeKey)
          if (node) {
            node.state = String(payload.state ?? (payload.success ? 'SUCCEEDED' : 'FAILED'))
            if (typeof payload.latencyMs === 'number') node.latencyMs = payload.latencyMs
          }
        }
        if (event.event === 'message_delta') {
          const delta = payload?.delta
          if (typeof delta === 'string') streamingText.value += delta
        }
        if (event.event === 'run_failed') {
          const reason = payload?.error
          error.value = typeof reason === 'string' ? reason : '工作流执行失败'
        }
      }, controller.signal)
    } catch (e) {
      if (!(e instanceof DOMException && e.name === 'AbortError')) {
        error.value = toMessage(e)
      }
    } finally {
      streaming.value = false
      controller = null
    }
  }

   
  async function loadEvaluation(): Promise<void> {
    try {
      const [summary, models] = await Promise.all([
        api.getEvaluationSummary(),
        api.getModelComparison(),
      ])
      evalSummary.value = summary
      modelComparison.value = models
    } catch (e) {
      error.value = toMessage(e)
    }
  }

   
  async function resumeWorkflow(): Promise<void> {
    if (!lastWorkflowRunId.value || streaming.value) return
    try {
      const result = await api.resumeWorkflow(lastWorkflowRunId.value)
      const lines = result.outcomes
        .map((o) => `- L${o.level} \`${o.nodeKey}\`: ${o.state} (${o.latencyMs}ms)`)
        .join('\n')
      messages.value.push({
        id: `resume-${Date.now()}`,
        conversationId: currentId.value,
        role: 'ASSISTANT',
        content: `**恢复运行** \`${result.runId}\` · ${result.status} · ${result.succeededNodes}/${result.totalNodes} 节点\n\n${lines}`,
        contentType: 'MARKDOWN',
        createdAt: new Date().toISOString(),
      })
    } catch (e) {
      error.value = toMessage(e)
    }
  }

  function cancel(): void {
    if (mode.value === 'agent' && currentRunId.value) {
      void api.cancelAgentRun(currentRunId.value).catch((e) => {
        error.value = toMessage(e)
      })
    }
    controller?.abort()
  }

  return {
    conversations,
    currentId,
    messages,
    events,
    tools,
    agents,
    currentPlan,
    workflowNodes,
    memoryStats,
    evalSummary,
    modelComparison,
    streamingText,
    streaming,
    error,
    mode,
    currentRunId,
    lastWorkflowRunId,
    init,
    loadConversations,
    loadTools,
    loadAgents,
    refreshMemoryStats,
    compactMemory,
    refreshMcpTools,
    loadTrace,
    loadEvaluation,
    selectConversation,
    newConversation,
    clearError,
    send,
    runWorkflowDemo,
    resumeWorkflow,
    cancel,
  }
})

function toMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}
