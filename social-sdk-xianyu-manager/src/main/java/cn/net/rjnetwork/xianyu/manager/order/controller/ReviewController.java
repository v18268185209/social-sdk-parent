package cn.net.rjnetwork.xianyu.manager.order.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.order.service.ReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 评价与信用 API — 评价/信用评价/信用及评价/退款 闭环。
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** 对指定订单发表评价 */
    @PostMapping("/orders/{orderId}")
    public ApiResponse<Map<String, Object>> reviewOrder(
            @RequestParam Long accountId,
            @PathVariable String orderId,
            @RequestParam String rating,
            @RequestParam(required = false) String content) {
        try {
            return ApiResponse.ok(reviewService.reviewOrder(accountId, orderId, rating, content));
        } catch (Exception e) {
            return ApiResponse.fail("REVIEW_FAILED", "评价失败: " + e.getMessage());
        }
    }

    /** 拉买家评价列表 */
    @GetMapping
    public ApiResponse<JsonNode> list(
            @RequestParam Long accountId,
            @RequestParam(required = false) String buyerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return ApiResponse.ok(reviewService.getReviewList(accountId, buyerId, page, pageSize));
        } catch (Exception e) {
            return ApiResponse.fail("REVIEW_LIST_FAILED", "拉评价列表失败: " + e.getMessage());
        }
    }

    /** 拉用户信用画像 */
    @GetMapping("/credit")
    public ApiResponse<JsonNode> credit(
            @RequestParam Long accountId,
            @RequestParam(required = false) String userId) {
        try {
            return ApiResponse.ok(reviewService.getUserCredit(accountId, userId));
        } catch (Exception e) {
            return ApiResponse.fail("CREDIT_FAILED", "拉信用失败: " + e.getMessage());
        }
    }

    /** 申请退款 */
    @PostMapping("/refunds")
    public ApiResponse<Map<String, Object>> applyRefund(
            @RequestParam Long accountId,
            @RequestParam String orderId,
            @RequestParam String reason,
            @RequestParam(required = false) String amount) {
        try {
            return ApiResponse.ok(reviewService.applyRefund(accountId, orderId, reason, amount));
        } catch (Exception e) {
            return ApiResponse.fail("REFUND_FAILED", "申请退款失败: " + e.getMessage());
        }
    }

    /** 退款列表 */
    @GetMapping("/refunds")
    public ApiResponse<JsonNode> refundList(
            @RequestParam Long accountId,
            @RequestParam(required = false) String disputeStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return ApiResponse.ok(reviewService.getRefundList(accountId, disputeStatus, page, pageSize));
        } catch (Exception e) {
            return ApiResponse.fail("REFUND_LIST_FAILED", "拉退款列表失败: " + e.getMessage());
        }
    }

    /** 退款详情 */
    @GetMapping("/refunds/{refundId}")
    public ApiResponse<JsonNode> refundDetail(
            @RequestParam Long accountId,
            @PathVariable String refundId) {
        try {
            return ApiResponse.ok(reviewService.getRefundDetail(accountId, refundId));
        } catch (Exception e) {
            return ApiResponse.fail("REFUND_DETAIL_FAILED", "拉退款详情失败: " + e.getMessage());
        }
    }
}
