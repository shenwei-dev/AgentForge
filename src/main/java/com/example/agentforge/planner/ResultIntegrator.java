package com.example.agentforge.planner;

import com.example.agentforge.agent.AgentManager;
import com.example.agentforge.agent.AgentProfile;
import com.example.agentforge.common.ExecutionEvent;
import com.example.agentforge.model.ChatMessage;
import com.example.agentforge.model.ModelAdapter;
import com.example.agentforge.planner.dto.AgentOutput;
import com.example.agentforge.planner.dto.TaskPlan;
import com.example.agentforge.runtime.Agent;
import com.example.agentforge.runtime.AgentContext;
import com.example.agentforge.runtime.AgentResult;
import com.example.agentforge.runtime.AgentRuntime;
import com.example.agentforge.tool.AgentScopedToolExecutor;
import com.example.agentforge.tool.ToolCallContext;
import com.example.agentforge.tool.ToolExecutor;
import com.example.agentforge.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

 

@Service
public class ResultIntegrator {

    private static final Logger log = LoggerFactory.getLogger(ResultIntegrator.class);

    private final AgentRuntime runtime;
    private final AgentManager manager;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;

    public ResultIntegrator(AgentRuntime runtime, AgentManager manager,
                            ToolExecutor toolExecutor, ToolRegistry toolRegistry) {
        this.runtime = runtime;
        this.manager = manager;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
    }

    public String integrate(TaskPlan plan, List<AgentOutput> outputs, ModelAdapter adapter,
                            String runId, String conversationId, Consumer<ExecutionEvent> events) {
        try {
            AgentProfile writer = manager.find(AgentManager.WRITER)
                    .orElseGet(() -> manager.get(AgentManager.GENERAL));
            Agent agent = manager.createAgent(writer.id(), toolRegistry.buildToolsPrompt());

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(agent.systemPrompt()
                    + "\n你现在负责整合多个 Agent 的结果，输出一份完整的 Markdown 最终报告，"
                    + "不要遗漏各角色的关键结论，也不要虚构未提供的信息。"));
            StringBuilder prompt = new StringBuilder();
            prompt.append("总目标：").append(plan.goal()).append("\n\n各 Agent 产出：\n");
            for (AgentOutput output : outputs) {
                prompt.append("### [").append(output.taskId()).append("] ")
                        .append(output.role()).append('\n')
                        .append(output.content()).append("\n\n");
            }
            messages.add(ChatMessage.user(prompt.toString()));

            AgentContext context = new AgentContext(runId, conversationId, writer.id(),
                    Clock.systemUTC(), manager.limitsFor(writer), messages);
            ToolExecutor scoped = new AgentScopedToolExecutor(toolExecutor, agent.allowedTools());
            ToolCallContext.set(runId, writer.id());
            AgentResult result;
            try {
                result = runtime.run(agent, context, adapter, scoped, filtered(events));
            } finally {
                ToolCallContext.clear();
            }
            if (result.isSuccess() && result.finalAnswer() != null) {
                events.accept(ExecutionEvent.agentCompleted(runId, conversationId));
                return result.finalAnswer();
            }
            log.warn("Writer 整合未成功，降级为拼接汇总: {}", result.terminationReason());
            return null;
        } catch (Exception e) {
            log.warn("Writer 整合异常，降级为拼接汇总", e);
            return null;
        }
    }

     
    private Consumer<ExecutionEvent> filtered(Consumer<ExecutionEvent> delegate) {
        return event -> {
            String type = event.event();
            if (ExecutionEvent.RUN_COMPLETED.equals(type)
                    || ExecutionEvent.RUN_FAILED.equals(type)
                    || ExecutionEvent.MESSAGE_DELTA.equals(type)) {
                return;
            }
            delegate.accept(event);
        };
    }
}
