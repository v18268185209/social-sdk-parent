package cn.net.rjnetwork.core.ai.model;

import java.io.Serializable;
import java.util.List;

/**
 * AI 请求体（对齐 OpenAI 兼容协议 POST /v1/chat/completions）
 */
public class AiRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模型标识（如 agnes-2.0-flash） */
    private String model;

    /** 对话消息序列 */
    private List<AiMessage> messages;

    /** 温度：越低越确定（0~2，默认 0.7） */
    private Double temperature = 0.7;

    /** 最大输出 token 数 */
    private Integer maxTokens = 1024;

    /** 核采样（0~1） */
    private Double topP;

    /** 是否流式输出 */
    private Boolean stream = false;

    /** 工具调用定义 */
    private List<AiTool> tools;

    /** 工具调用控制（auto / none / 指定工具） */
    private Object toolChoice;

    /** 启用 Thinking 模式（部分厂商支持） */
    private Boolean enableThinking;

    /** Thinking 预算 token 数 */
    private Integer thinkingBudgetTokens;

    public AiRequest() {}

    public static AiRequest of(String model, List<AiMessage> messages) {
        AiRequest r = new AiRequest();
        r.model = model;
        r.messages = messages;
        return r;
    }

    // ----- getters / setters -----

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<AiMessage> getMessages() { return messages; }
    public void setMessages(List<AiMessage> messages) { this.messages = messages; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }

    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }

    public List<AiTool> getTools() { return tools; }
    public void setTools(List<AiTool> tools) { this.tools = tools; }

    public Object getToolChoice() { return toolChoice; }
    public void setToolChoice(Object toolChoice) { this.toolChoice = toolChoice; }

    public Boolean getEnableThinking() { return enableThinking; }
    public void setEnableThinking(Boolean enableThinking) { this.enableThinking = enableThinking; }

    public Integer getThinkingBudgetTokens() { return thinkingBudgetTokens; }
    public void setThinkingBudgetTokens(Integer thinkingBudgetTokens) { this.thinkingBudgetTokens = thinkingBudgetTokens; }

    /** 工具定义 */
    public static class AiTool implements Serializable {
        private static final long serialVersionUID = 1L;
        private String type = "function";
        private AiFunction function;

        public AiTool() {}
        public AiTool(String name, String description, Object parameters) {
            this.function = new AiFunction(name, description, parameters);
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public AiFunction getFunction() { return function; }
        public void setFunction(AiFunction function) { this.function = function; }
    }

    public static class AiFunction implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String description;
        /** JSON Schema 对象 */
        private Object parameters;

        public AiFunction() {}
        public AiFunction(String name, String description, Object parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Object getParameters() { return parameters; }
        public void setParameters(Object parameters) { this.parameters = parameters; }
    }
}
