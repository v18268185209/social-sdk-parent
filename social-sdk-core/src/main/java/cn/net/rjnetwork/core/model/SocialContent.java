package cn.net.rjnetwork.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 社交平台发布内容
 */
public class SocialContent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String text;
    private List<String> imageUrls;
    private List<String> videoUrls;
    private String linkUrl;
    private String linkTitle;
    private String linkDescription;
    private String linkThumbnailUrl;
    private Instant scheduledTime;
    private Boolean isDraft;
    private List<String> tags;
    private String location;
    private Double latitude;
    private Double longitude;
    private Integer visibility;
    private String rawData;

    public SocialContent() {
        this.imageUrls = new ArrayList<>();
        this.videoUrls = new ArrayList<>();
        this.tags = new ArrayList<>();
    }

    public SocialContent(String text) {
        this();
        this.text = text;
    }

    // Builder pattern for convenience
    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public List<String> getVideoUrls() {
        return videoUrls;
    }

    public void setVideoUrls(List<String> videoUrls) {
        this.videoUrls = videoUrls;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public String getLinkTitle() {
        return linkTitle;
    }

    public void setLinkTitle(String linkTitle) {
        this.linkTitle = linkTitle;
    }

    public String getLinkDescription() {
        return linkDescription;
    }

    public void setLinkDescription(String linkDescription) {
        this.linkDescription = linkDescription;
    }

    public String getLinkThumbnailUrl() {
        return linkThumbnailUrl;
    }

    public void setLinkThumbnailUrl(String linkThumbnailUrl) {
        this.linkThumbnailUrl = linkThumbnailUrl;
    }

    public Instant getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(Instant scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public Boolean getIsDraft() {
        return isDraft;
    }

    public void setIsDraft(Boolean isDraft) {
        this.isDraft = isDraft;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Integer getVisibility() {
        return visibility;
    }

    public void setVisibility(Integer visibility) {
        this.visibility = visibility;
    }

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public static class Builder {
        private final SocialContent content = new SocialContent();

        public Builder text(String text) {
            content.setText(text);
            return this;
        }

        public Builder addImageUrl(String url) {
            content.getImageUrls().add(url);
            return this;
        }

        public Builder imageUrls(List<String> urls) {
            content.setImageUrls(urls);
            return this;
        }

        public Builder addVideoUrl(String url) {
            content.getVideoUrls().add(url);
            return this;
        }

        public Builder linkUrl(String url) {
            content.setLinkUrl(url);
            return this;
        }

        public Builder linkTitle(String title) {
            content.setLinkTitle(title);
            return this;
        }

        public Builder linkDescription(String description) {
            content.setLinkDescription(description);
            return this;
        }

        public Builder scheduledTime(Instant time) {
            content.setScheduledTime(time);
            return this;
        }

        public Builder addTag(String tag) {
            content.getTags().add(tag);
            return this;
        }

        public Builder location(String location) {
            content.setLocation(location);
            return this;
        }

        public Builder visibility(Integer visibility) {
            content.setVisibility(visibility);
            return this;
        }

        public SocialContent build() {
            return content;
        }
    }

    @Override
    public String toString() {
        return "SocialContent{" +
                "text='" + (text != null ? text.substring(0, Math.min(text.length(), 50)) + "..." : null) + "'" +
                ", imageCount=" + (imageUrls != null ? imageUrls.size() : 0) +
                ", videoCount=" + (videoUrls != null ? videoUrls.size() : 0) +
                '}';
    }
}
