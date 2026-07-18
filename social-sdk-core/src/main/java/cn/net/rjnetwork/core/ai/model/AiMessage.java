package cn.net.rjnetwork.core.ai.model;

import java.io.Serializable;
import java.util.List;

/**
 * AI 消息体（对齐 OpenAI 兼容协议）
 */
public class AiMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色：system / user / assistant / tool */
    private String role;

    /** 纯文本内容（简单场景） */
    private String content;

    /** 富文本内容块（图文混排时使用） */
    private List<ContentBlock> contentBlocks;

    /** 工具调用 ID（role=tool 时必填） */
    private String toolCallId;

    public AiMessage() {}

    public AiMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static AiMessage system(String content) {
        return new AiMessage("system", content);
    }

    public static AiMessage user(String content) {
        return new AiMessage("user", content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage("assistant", content);
    }

    // ----- getters / setters -----

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<ContentBlock> getContentBlocks() { return contentBlocks; }
    public void setContentBlocks(List<ContentBlock> contentBlocks) { this.contentBlocks = contentBlocks; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    /**
     * 单条内容块（文本或图片 URL）
     */
    public static class ContentBlock implements Serializable {
        private static final long serialVersionUID = 1L;

        /** text | image_url */
        private String type;
        private String text;
        private ImageUrl imageUrl;

        public static ContentBlock text(String text) {
            ContentBlock b = new ContentBlock();
            b.type = "text";
            b.text = text;
            return b;
        }

        public static ContentBlock imageUrl(String url) {
            ContentBlock b = new ContentBlock();
            b.type = "image_url";
            b.imageUrl = new ImageUrl(url);
            return b;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public ImageUrl getImageUrl() { return imageUrl; }
        public void setImageUrl(ImageUrl imageUrl) { this.imageUrl = imageUrl; }
    }

    public static class ImageUrl implements Serializable {
        private static final long serialVersionUID = 1L;
        private String url;

        public ImageUrl() {}
        public ImageUrl(String url) { this.url = url; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
