package com.example.agentforge.trace;

import com.example.agentforge.common.ExecutionEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

 

@Service
public class TraceManager {

    private final ExecutionEventStore store;

    public TraceManager(ExecutionEventStore store) {
        this.store = store;
    }

     
    public Consumer<ExecutionEvent> sink() {
        return store::record;
    }

     
    public Consumer<ExecutionEvent> sink(Consumer<ExecutionEvent> delegate) {
        return event -> {
            store.record(event);
            delegate.accept(event);
        };
    }

    public List<ExecutionEvent> eventsByRun(String runId) {
        return store.findByRun(runId);
    }

    public List<ExecutionEvent> eventsByConversation(String conversationId) {
        return store.findByConversation(conversationId);
    }

     
    public List<RunSummary> runsByConversation(String conversationId) {
        List<ExecutionEvent> events = eventsByConversation(conversationId);
        Map<String, List<ExecutionEvent>> grouped = events.stream()
                .filter(event -> event.runId() != null)
                .collect(Collectors.groupingBy(ExecutionEvent::runId,
                        LinkedHashMap::new, Collectors.toList()));
        List<RunSummary> summaries = new ArrayList<>();
        grouped.forEach((runId, list) -> {
            long first = list.stream().mapToLong(ExecutionEvent::timestamp).min().orElse(0L);
            long last = list.stream().mapToLong(ExecutionEvent::timestamp).max().orElse(0L);
            summaries.add(new RunSummary(runId, list.get(0).conversationId(),
                    first, last, list.size(), list.get(list.size() - 1).event()));
        });
        summaries.sort(Comparator.comparingLong(RunSummary::firstEventAt).reversed());
        return summaries;
    }
}
