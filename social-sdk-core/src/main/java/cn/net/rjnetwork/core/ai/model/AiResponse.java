package cn.net.rjnetwork.core.ai.model;

import java.io.Serializable;
import java.util.List;

/**
 * AI 响应体（对齐 OpenAI 兼容协议 /v1/chat/completions 返回）
 */
public class AiResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    // ----- getters / setters -----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<Choice> getChoices() { return choices; }
    public void setChoices(List<Choice> choices) { this.choices = choices; }

    public Usage getUsage() { return usage; }
    public void setUsage(Usage usage) { this.usage = usage; }

    /** 抽取第一条回复内容（纯文本场景常用） */
    public String firstContent() {
        if (choices == null || choices.isEmpty()) return null;
        Choice c = choices.get(0);
        if (c.getMessage() == null) return null;
        return c.getMessage().getContent();
    }

    public static class Choice implements Serializable {
        private static final long serialVersionUID = 1L;
        private int index;
        private AiMessage message;
        /** stop / length / tool_calls */
        private String finishReason;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        public AiMessage getMessage() { return message; }
        public void setMessage(AiMessage message) { this.message = message; }

        public String getFinishReason() { return finishReason; }
        public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
    }

    public static class Usage implements Serializable {
        private static final long serialVersionUID = 1L;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;

        public int getPromptTokens() { return promptTokens; }
        public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }

        public int getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }

        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    }
}
