package com.example.agentforge.runtime;

import java.util.Set;

 

public record DefaultAgent(String id, String systemPrompt, Set<String> allowedTools) implements Agent {

    public DefaultAgent {
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
    }

     
    public static DefaultAgent general(Set<String> tools) {
        return new DefaultAgent("general", GENERAL_SYSTEM_PROMPT, tools);
    }

     
    public static DefaultAgent general(Set<String> tools, String toolDescriptions) {
        return new DefaultAgent("general", buildSystemPrompt(toolDescriptions), tools);
    }

    public static String buildSystemPrompt(String toolDescriptions) {
        return BASE_PROMPT + "\n可用工具（名称、描述与参数 Schema）：\n" + toolDescriptions;
    }

    private static final String BASE_PROMPT = """
            你是一个由 AgentForge Runtime 驱动的自主决策 Agent。
            每一轮只能输出一个 JSON 对象，禁止输出 Markdown 代码块、解释文字或任何多余内容。
            协议格式：
            {"thought":"简短推理","decision":"tool 或 final","tool_name":"工具名","tool_input":{"参数名":"值"},"answer":"最终答案"}
            规则：
            1. decision=tool 时，tool_name 与 tool_input 必填，参数必须符合对应工具的 Schema；
            2. decision=final 时，answer 必填，内容较长时可使用 Markdown；
            3. 工具不存在、权限被拒、参数校验失败或工具连续失败时，改用 decision=final 直接给出答案；
            4. 你只负责提出决策，是否执行、执行几轮、何时停止由 Runtime 决定。
            """;

    private static final String DEFAULT_TOOLS_DESCRIPTION =
            "- echo: 原样返回输入文本，参数 {\"text\":\"字符串\"}\n";

    public static final String GENERAL_SYSTEM_PROMPT = buildSystemPrompt(DEFAULT_TOOLS_DESCRIPTION);
}
