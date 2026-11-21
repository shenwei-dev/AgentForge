package com.example.agentforge.evaluation;

import java.util.ArrayList;
import java.util.List;

 
public class EvaluationTask {

    private String id;
    private String name;
    private String category;
    private String prompt;
    private List<String> expectedKeywords = new ArrayList<>();
    private String expectedTool;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public List<String> getExpectedKeywords() { return expectedKeywords; }
    public void setExpectedKeywords(List<String> expectedKeywords) {
        this.expectedKeywords = expectedKeywords == null ? new ArrayList<>() : expectedKeywords;
    }
    public String getExpectedTool() { return expectedTool; }
    public void setExpectedTool(String expectedTool) { this.expectedTool = expectedTool; }
}
