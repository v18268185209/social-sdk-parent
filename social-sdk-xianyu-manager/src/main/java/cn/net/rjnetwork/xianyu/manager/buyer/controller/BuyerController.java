package cn.net.rjnetwork.xianyu.manager.buyer.controller;

import cn.net.rjnetwork.xianyu.manager.buyer.model.BuyerProfile;
import cn.net.rjnetwork.xianyu.manager.buyer.service.BuyerProfileService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 买家画像 API
 */
@RestController
@RequestMapping("/api/buyer")
public class BuyerController {

    private final BuyerProfileService service;

    public BuyerController(BuyerProfileService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<BuyerProfile>> list(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String keyword) {
        return ApiResponse.success(service.list(page, size, keyword));
    }

    @GetMapping("/{buyerId}")
    public ApiResponse<BuyerProfile> get(@PathVariable String buyerId) {
        return ApiResponse.success(service.get(buyerId));
    }

    @PostMapping("/{buyerId}/tag")
    public ApiResponse<String> addTag(@PathVariable String buyerId, @RequestParam String tag) {
        service.addTag(buyerId, tag);
        return ApiResponse.success("ok");
    }

    @DeleteMapping("/{buyerId}/tag")
    public ApiResponse<String> removeTag(@PathVariable String buyerId, @RequestParam String tag) {
        service.removeTag(buyerId, tag);
        return ApiResponse.success("ok");
    }

    @PostMapping("/{buyerId}/notes")
    public ApiResponse<String> setNotes(@PathVariable String buyerId, @RequestParam String notes) {
        service.setNotes(buyerId, notes);
        return ApiResponse.success("ok");
    }

    @GetMapping("/{buyerId}/credibility")
    public ApiResponse<Double> getCredibility(@PathVariable String buyerId) {
        BuyerProfile p = service.get(buyerId);
        return ApiResponse.success(p != null ? p.getCredibilityScore() : 50.0);
    }
}
