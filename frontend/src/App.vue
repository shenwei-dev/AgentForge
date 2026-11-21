<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import { useChatStore } from './stores/chat'
import type { ExecutionEvent } from './types'

const store = useChatStore()
const input = ref('')
const bottomRef = ref<HTMLElement | null>(null)
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const renderedMessages = computed(() =>
  store.messages.map((message) => ({ ...message, html: md.render(message.content ?? '') })),
)
const streamingHtml = computed(() => md.render(store.streamingText))

const currentRound = computed(() => {
  const rounds = store.events.filter((event) => event.event === 'round_started')
  return rounds.length ? Number(rounds[rounds.length - 1].payload?.round ?? 0) : 0
})

const workflowLevels = computed(() =>
  [...new Set(store.workflowNodes.map((node) => node.level))].sort((a, b) => a - b),
)

const emptyHint = computed(() => {
  if (store.mode === 'plan') return '试试：研究 Spring AI 和 LangChain4j 的区别，并生成一份对比报告'
  if (store.mode === 'agent') return '试试：用 calculator 工具计算 (12+3)*4，再说明结果'
  return '输入一个任务开始对话，例如：帮我对比 Spring AI 和 LangChain4j'
})

function nodesAtLevel(level: number) {
  return store.workflowNodes.filter((node) => node.level === level)
}

function nodeTagType(state: string): 'success' | 'warning' | 'danger' | 'info' {
  if (state === 'SUCCEEDED') return 'success'
  if (state === 'FAILED') return 'danger'
  if (state === 'RUNNING') return 'warning'
  return 'info'
}

function traceDetail(event: ExecutionEvent): string {
  const payload = event.payload
  if (!payload) return ''
  if (typeof payload.round === 'number') return ` · 第 ${payload.round} 轮`
  if (typeof payload.taskId === 'string') {
    const progress = typeof payload.index === 'number' && typeof payload.total === 'number'
      ? ` (${payload.index}/${payload.total})` : ''
    const status = payload.success === false ? '（失败）' : ''
    const role = typeof payload.agentRole === 'string' ? ` → ${payload.agentRole}` : ''
    return ` · ${payload.taskId}${progress}${status}${role}`
  }
  if (typeof payload.nodeKey === 'string') {
    return ` · ${payload.nodeKey} (L${String(payload.level ?? 0)}) ${String(payload.state ?? '')}`
  }
  if (typeof payload.toolName === 'string') {
    return ` · ${payload.toolName}${payload.success === false ? '（失败）' : ''}`
  }
  return ''
}

function formatSchema(schema: Record<string, unknown>): string {
  return JSON.stringify(schema, null, 2)
}

onMounted(() => {
  void store.init()
  void store.loadEvaluation()
})

watch(
  () => [store.messages.length, store.streamingText.length],
  async () => {
    await nextTick()
    bottomRef.value?.scrollIntoView({ block: 'end' })
  },
)

function submit(): void {
  const text = input.value.trim()
  if (!text || store.streaming) return
  input.value = ''
  void store.send(text)
}

function onKeydown(event: Event): void {
  const keyboardEvent = event as KeyboardEvent
  if (keyboardEvent.key === 'Enter' && !keyboardEvent.shiftKey) {
    keyboardEvent.preventDefault()
    submit()
  }
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        AgentForge
        <small>企业级智能体运行时与编排引擎</small>
      </div>
      <el-button class="new-btn" type="primary" plain @click="store.newConversation()">
        + 新建会话
      </el-button>
      <div class="conv-list">
        <div
          v-for="conversation in store.conversations"
          :key="conversation.id"
          :class="['conv-item', { active: conversation.id === store.currentId }]"
          @click="store.selectConversation(conversation.id)"
        >
          <span class="conv-title">{{ conversation.title }}</span>
        </div>
      </div>
      <div class="sidebar-footer">Stage 10+ · Evaluation &amp; Ops</div>
    </aside>

    <main class="main">
      <header class="header">
        <div class="header-left">
          <span>对话</span>
          <el-radio-group v-model="store.mode" size="small" :disabled="store.streaming">
            <el-radio-button value="chat">基础聊天</el-radio-button>
            <el-radio-button value="agent">Agent Runtime</el-radio-button>
            <el-radio-button value="plan">任务规划</el-radio-button>
          </el-radio-group>
        </div>
        <div class="header-right">
          <el-button size="small" :disabled="store.streaming" @click="store.runWorkflowDemo()">
            运行示例 DAG
          </el-button>
          <el-button
            size="small"
            :disabled="store.streaming || !store.lastWorkflowRunId"
            @click="store.resumeWorkflow()"
          >
            恢复运行
          </el-button>
          <el-tag v-if="currentRound > 0" size="small" type="info">第 {{ currentRound }} 轮</el-tag>
          <el-tag size="small" :type="store.streaming ? 'warning' : 'success'">
            {{ store.streaming ? '执行中' : '就绪' }}
          </el-tag>
        </div>
      </header>

      <div class="content">
        <section class="chat-panel">
          <div class="messages">
            <div v-if="!store.messages.length && !store.streaming" class="empty">{{ emptyHint }}</div>
            <div
              v-for="message in renderedMessages"
              :key="message.id"
              :class="['bubble', message.role.toLowerCase()]"
            >
              <div class="bubble-role">{{ message.role === 'USER' ? '你' : 'AgentForge' }}</div>
              <div class="markdown-body" v-html="message.html" />
            </div>
            <div v-if="store.streaming" class="bubble assistant">
              <div class="bubble-role">AgentForge <span class="typing">执行中…</span></div>
              <div class="markdown-body" v-html="streamingHtml" />
            </div>
            <div ref="bottomRef" />
          </div>

          <el-alert
            v-if="store.error"
            :title="store.error"
            type="error"
            show-icon
            closable
            @close="store.clearError()"
          />

          <div class="composer">
            <el-input
              v-model="input"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="输入任务，Enter 发送，Shift+Enter 换行"
              @keydown="onKeydown"
            />
            <div class="actions">
              <el-button v-if="store.streaming" type="danger" plain @click="store.cancel()">停止</el-button>
              <el-button type="primary" :disabled="store.streaming || !input.trim()" @click="submit">
                发送
              </el-button>
            </div>
          </div>
        </section>

        <section class="trace-panel">
          <div v-if="store.workflowNodes.length" class="workflow-section">
            <div class="trace-title">DAG 节点 <span>{{ store.workflowNodes.length }} 个</span></div>
            <div v-for="level in workflowLevels" :key="level" class="wf-level">
              <span class="wf-level-label">L{{ level }}</span>
              <el-tag
                v-for="node in nodesAtLevel(level)"
                :key="node.nodeKey"
                size="small"
                :type="nodeTagType(node.state)"
              >
                {{ node.nodeKey }} · {{ node.state }}
              </el-tag>
            </div>
          </div>

          <div v-if="store.currentPlan" class="plan-section">
            <div class="trace-title">任务计划 <span>{{ store.currentPlan.tasks.length }} 个任务</span></div>
            <div class="plan-goal">{{ store.currentPlan.goal }}</div>
            <div v-for="task in store.currentPlan.tasks" :key="task.id" class="plan-task">
              <el-tag size="small" type="primary">{{ task.id }}</el-tag>
              <el-tag v-if="task.agentRole" size="small" type="success">{{ task.agentRole }}</el-tag>
              <span class="plan-title">{{ task.title }}</span>
              <span v-if="task.dependsOn?.length" class="plan-dep">← {{ task.dependsOn.join(', ') }}</span>
            </div>
          </div>

          <div class="trace-title">
            执行过程 <span>{{ store.events.length }} 事件</span>
            <el-button size="small" text @click="store.loadTrace()">加载 Trace</el-button>
          </div>
          <div class="trace-list">
            <div v-for="event in store.events" :key="event.eventId" class="trace-item">
              <span class="trace-time">{{ new Date(event.timestamp).toLocaleTimeString() }}</span>
              <el-tag size="small" :type="event.level === 'ERROR' ? 'danger' : 'info'">
                {{ event.event }}
              </el-tag>
              <span class="trace-msg">
                {{ event.message || '' }}<span class="trace-detail">{{ traceDetail(event) }}</span>
              </span>
            </div>
            <div v-if="!store.events.length" class="empty" style="margin-top: 40px">暂无执行事件</div>
          </div>

          <div class="eval-section">
            <div class="trace-title">
              评测
              <span>{{ store.evalSummary ? `${store.evalSummary.succeededRuns}/${store.evalSummary.totalRuns} 成功` : '未加载' }}</span>
              <el-button size="small" text @click="store.loadEvaluation()">刷新</el-button>
            </div>
            <div v-if="store.evalSummary" class="memory-grid">
              <span>成功率 {{ (store.evalSummary.successRate * 100).toFixed(0) }}%</span>
              <span>平均耗时 {{ store.evalSummary.averageLatencyMs.toFixed(0) }}ms</span>
              <span>Token {{ store.evalSummary.totalTokens }}</span>
              <span>工具 {{ store.evalSummary.totalToolCalls }} 次 / {{ (store.evalSummary.toolSuccessRate * 100).toFixed(0) }}%</span>
            </div>
            <div v-for="item in store.modelComparison" :key="item.modelName" class="eval-model">
              <span>{{ item.modelName }}</span>
              <span>{{ item.runs }} runs · {{ (item.successRate * 100).toFixed(0) }}% · {{ item.averageLatencyMs.toFixed(0) }}ms</span>
            </div>
          </div>

          <div class="memory-section">
            <div class="trace-title">
              上下文 <span>{{ store.memoryStats?.contextChars ?? 0 }} 字符</span>
            </div>
            <div v-if="store.memoryStats" class="memory-grid">
              <span>历史 {{ store.memoryStats.historyCount }}</span>
              <span>保留 {{ store.memoryStats.retainedCount }}</span>
              <span>压缩 {{ store.memoryStats.compactedCount }}</span>
              <span>摘要 {{ store.memoryStats.summaryPresent ? '有' : '无' }}</span>
              <span>可复用 {{ store.memoryStats.reusableResults }}</span>
            </div>
            <el-button size="small" text :disabled="!store.currentId" @click="store.compactMemory()">
              手动压缩
            </el-button>
          </div>

          <div class="agent-section">
            <div class="trace-title">Agent 角色 <span>{{ store.agents.length }} 个</span></div>
            <el-collapse>
              <el-collapse-item
                v-for="role in store.agents"
                :key="role.id"
                :title="`${role.id} · ${role.name}`"
              >
                <div class="tool-desc">
                  {{ role.role }} · 最大 {{ role.maxRounds }} 轮 · 超时 {{ role.timeoutMs }}ms
                </div>
                <div class="tool-meta">工具: {{ role.allowedTools.join(', ') || '无' }}</div>
              </el-collapse-item>
            </el-collapse>
          </div>

          <div class="tool-section">
            <div class="trace-title">
              已注册工具 <span>{{ store.tools.length }} 个</span>
              <el-button size="small" text @click="store.refreshMcpTools()">刷新 MCP</el-button>
            </div>
            <el-collapse>
              <el-collapse-item
                v-for="tool in store.tools"
                :key="tool.name"
                :title="`${tool.name} · ${tool.category}`"
              >
                <div class="tool-desc">{{ tool.description }}</div>
                <div class="tool-meta">
                  权限: {{ tool.requiredPermissions.join(', ') }} · 超时: {{ tool.timeoutMs }}ms
                </div>
                <pre class="tool-schema">{{ formatSchema(tool.jsonSchema) }}</pre>
              </el-collapse-item>
            </el-collapse>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.header-left,
.header-right { display: flex; align-items: center; gap: 12px; }
.trace-detail { color: #2563eb; }
.workflow-section {
  flex-shrink: 0;
  max-height: 25%;
  overflow-y: auto;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 10px;
  margin-bottom: 10px;
}
.wf-level {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px 0;
}
.wf-level-label {
  font-size: 11px;
  color: #6b7280;
  min-width: 24px;
  font-variant-numeric: tabular-nums;
}
.plan-section {
  flex-shrink: 0;
  max-height: 25%;
  overflow-y: auto;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 10px;
  margin-bottom: 10px;
}
.plan-goal { font-size: 12px; color: #6b7280; margin: 8px 0; line-height: 1.5; }
.plan-task {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  padding: 5px 0;
  border-bottom: 1px dashed #f1f5f9;
}
.plan-title { color: #1f2937; }
.plan-dep { color: #9ca3af; font-size: 11px; }
.memory-section {
  flex-shrink: 0;
  border-top: 1px solid #e5e7eb;
  margin-top: 10px;
  padding-top: 10px;
}
.memory-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  font-size: 11px;
  color: #6b7280;
  margin: 8px 0;
}
.eval-section {
  flex-shrink: 0;
  border-top: 1px solid #e5e7eb;
  margin-top: 10px;
  padding-top: 10px;
}
.eval-model {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  color: #374151;
  padding: 3px 0;
  border-bottom: 1px dashed #f1f5f9;
}
.agent-section,
.tool-section {
  flex-shrink: 0;
  max-height: 18%;
  overflow-y: auto;
  border-top: 1px solid #e5e7eb;
  margin-top: 10px;
  padding-top: 10px;
}
.tool-desc { font-size: 12px; color: #374151; margin-bottom: 4px; }
.tool-meta { font-size: 11px; color: #6b7280; margin-bottom: 6px; }
.tool-schema {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 8px;
  font-size: 11px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
