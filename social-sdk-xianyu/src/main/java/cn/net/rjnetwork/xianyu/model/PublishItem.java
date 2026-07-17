package cn.net.rjnetwork.xianyu.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品发布参数。承载标题、描述、图片、视频、价格、分类、发货、库存等发布表单字段。
 *
 * <p>该模型对齐闲鱼「发闲置」表单的字段集，所有字段均可选（除标题外），
 * 未设置的字段在 SDK 内跳过对应步骤，便于在不同页面状态下复用同一参数对象。</p>
 *
 * <p>草稿与正式发布共用同一模型：{@code publish=false} 仅填写不提交，
 * {@code publish=true} 在填写后触发「发布」按钮。</p>
 */
public class PublishItem {

    /** 标题（必填，闲鱼限制 ≤30 字）。 */
    private String title;

    /** 描述/正文（支持长文本，闲鱼编辑器为富文本）。 */
    private String description;

    /** 本地图片路径或可访问 URL 列表（按顺序上传，闲鱼最多 9 张）。 */
    private List<String> images = new ArrayList<>();

    /** 本地短视频路径或可访问 URL 列表（闲鱼最多 1 个）。 */
    private List<String> videos = new ArrayList<>();

    /** 价格（单位元，闲鱼最小 0.01）。 */
    private Double price;

    /** 原价（可选，用于划线价）。 */
    private Double originalPrice;

    /** 分类名称（如「数码」），SDK 会尝试在分类选择器中匹配。 */
    private String category;

    /** 发货方式（如「包邮」、「EMS」）。 */
    private String delivery;

    /** 库存数量（默认 1）。 */
    private Integer stock = 1;

    /** 是否在填写完成后点击「发布」按钮。false = 仅草稿。 */
    private boolean publish = false;

    /** 发布后期望出现在页面上的关键词（用于判定发布成功，默认用 title）。 */
    private String successKeyword;

    // ----- getters/setters -----

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images == null ? new ArrayList<>() : images; }

    public List<String> getVideos() { return videos; }
    public void setVideos(List<String> videos) { this.videos = videos == null ? new ArrayList<>() : videos; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(Double originalPrice) { this.originalPrice = originalPrice; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDelivery() { return delivery; }
    public void setDelivery(String delivery) { this.delivery = delivery; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public boolean isPublish() { return publish; }
    public void setPublish(boolean publish) { this.publish = publish; }

    public String getSuccessKeyword() { return successKeyword; }
    public void setSuccessKeyword(String successKeyword) { this.successKeyword = successKeyword; }
}
