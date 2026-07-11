package cn.net.rjnetwork.demo.model;

public class SendMessageRequest {

    private String demoSessionId;
    private String toUserId;
    private String text;
    private String itemId;
    private String cid;
    private Boolean useRealtime;
    private String imageUrl;
    private String imagePath;
    private Integer imageWidth;
    private Integer imageHeight;

    public String getDemoSessionId() {
        return demoSessionId;
    }

    public void setDemoSessionId(String demoSessionId) {
        this.demoSessionId = demoSessionId;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getCid() {
        return cid;
    }

    public void setCid(String cid) {
        this.cid = cid;
    }

    public Boolean getUseRealtime() {
        return useRealtime;
    }

    public void setUseRealtime(Boolean useRealtime) {
        this.useRealtime = useRealtime;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public Integer getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(Integer imageWidth) {
        this.imageWidth = imageWidth;
    }

    public Integer getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(Integer imageHeight) {
        this.imageHeight = imageHeight;
    }
}
