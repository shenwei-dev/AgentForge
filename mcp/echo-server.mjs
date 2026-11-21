

import readline from 'node:readline'

const PROTOCOL_VERSION = '2024-11-05'

const TOOLS = [
  {
    name: 'echo',
    description: '回显输入文本（MCP 外部工具）',
    inputSchema: {
      type: 'object',
      properties: { text: { type: 'string', description: '要回显的文本' } },
      required: ['text'],
    },
  },
  {
    name: 'upper',
    description: '把输入文本转换为大写',
    inputSchema: {
      type: 'object',
      properties: { text: { type: 'string', description: '要转换的文本' } },
      required: ['text'],
    },
  },
  {
    name: 'sum',
    description: '计算两个数字之和',
    inputSchema: {
      type: 'object',
      properties: {
        a: { type: 'number', description: '第一个数字' },
        b: { type: 'number', description: '第二个数字' },
      },
      required: ['a', 'b'],
    },
  },
]

function send(message) {
  process.stdout.write(JSON.stringify(message) + '\n')
}

function error(id, code, message) {
  send({ jsonrpc: '2.0', id, error: { code, message } })
}

function callTool(name, args) {
  switch (name) {
    case 'echo':
      return `Echo: ${String(args.text ?? '')}`
    case 'upper':
      return String(args.text ?? '').toUpperCase()
    case 'sum': {
      const a = Number(args.a)
      const b = Number(args.b)
      if (Number.isNaN(a) || Number.isNaN(b)) {
        throw new Error('a 与 b 必须是数字')
      }
      return String(a + b)
    }
    default:
      throw new Error(`未知工具: ${name}`)
  }
}

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity })

rl.on('line', (line) => {
  if (!line.trim()) return
  let message
  try {
    message = JSON.parse(line)
  } catch {
    console.error('[mcp] 非法 JSON:', line)
    return
  }

  
  if (message.method && message.method.startsWith('notifications/')) return

  try {
    switch (message.method) {
      case 'initialize':
        send({
          jsonrpc: '2.0',
          id: message.id,
          result: {
            protocolVersion: PROTOCOL_VERSION,
            capabilities: { tools: {} },
            serverInfo: { name: 'agentforge-demo-mcp', version: '1.0.0' },
          },
        })
        break
      case 'tools/list':
        send({ jsonrpc: '2.0', id: message.id, result: { tools: TOOLS } })
        break
      case 'tools/call': {
        const params = message.params ?? {}
        const text = callTool(params.name, params.arguments ?? {})
        send({
          jsonrpc: '2.0',
          id: message.id,
          result: { content: [{ type: 'text', text }], isError: false },
        })
        break
      }
      default:
        error(message.id ?? null, -32601, `Method not found: ${message.method}`)
    }
  } catch (e) {
    send({
      jsonrpc: '2.0',
      id: message.id ?? null,
      result: { content: [{ type: 'text', text: e.message }], isError: true },
    })
  }
})

console.error('[mcp] agentforge-demo-mcp server started')
