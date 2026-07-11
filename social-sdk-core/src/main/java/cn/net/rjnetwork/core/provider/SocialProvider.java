package cn.net.rjnetwork.core.provider;

import cn.net.rjnetwork.core.constant.SocialPlatform;
import cn.net.rjnetwork.core.exception.SocialAuthenticationException;
import cn.net.rjnetwork.core.exception.SocialNetworkException;
import cn.net.rjnetwork.core.model.PostResult;
import cn.net.rjnetwork.core.model.SocialContent;
import cn.net.rjnetwork.core.model.SocialSession;
import cn.net.rjnetwork.core.model.SocialUserProfile;

/**
 * 社交平台提供者接口
 * 定义了所有社交平台实现必须提供的基本功能
 */
public interface SocialProvider {

    /**
     * 获取支持的平台类型
     */
    SocialPlatform getPlatform();

    /**
     * 检查是否支持该平台
     */
    default boolean supports(SocialPlatform platform) {
        return platform == getPlatform();
    }

    /**
     * 检查会话是否有效
     */
    default boolean isSessionValid(SocialSession session) {
        return session != null && session.isValid();
    }

    /**
     * 认证用户
     * @param credentials 认证凭证（具体实现决定格式）
     * @return 用户会话信息
     * @throws SocialAuthenticationException 认证失败异常
     */
    SocialSession authenticate(Object credentials) throws SocialAuthenticationException;

    /**
     * 刷新会话令牌
     * @param session 当前会话
     * @return 新的会话信息
     * @throws SocialAuthenticationException 刷新失败异常
     */
    SocialSession refreshSession(SocialSession session) throws SocialAuthenticationException;

    /**
     * 获取当前用户信息
     * @param session 用户会话
     * @return 用户信息
     * @throws SocialAuthenticationException 会话无效异常
     */
    SocialUserProfile getUserProfile(SocialSession session) throws SocialAuthenticationException;

    /**
     * 发布内容
     * @param session 用户会话
     * @param content 要发布的内容
     * @return 发布结果
     * @throws SocialNetworkException 网络异常
     * @throws SocialAuthenticationException 会话无效异常
     */
    PostResult postContent(SocialSession session, SocialContent content)
            throws SocialNetworkException, SocialAuthenticationException;

    /**
     * 删除内容
     * @param session 用户会话
     * @param postId 要删除的帖子ID
     * @return 是否删除成功
     * @throws SocialAuthenticationException 会话无效异常
     */
    boolean deletePost(SocialSession session, String postId) throws SocialAuthenticationException;

    /**
     * 获取内容详情
     * @param session 用户会话
     * @param postId 帖子ID
     * @return 内容详情（具体实现决定格式）
     * @throws SocialAuthenticationException 会话无效异常
     */
    Object getPostDetail(SocialSession session, String postId) throws SocialAuthenticationException;

    /**
     * 获取用户时间线
     * @param session 用户会话
     * @param limit 获取数量限制
     * @return 内容列表（具体实现决定格式）
     * @throws SocialAuthenticationException 会话无效异常
     */
    Object getTimeline(SocialSession session, int limit) throws SocialAuthenticationException;

    /**
     * 验证会话是否仍然有效
     * @param session 要验证的会话
     * @return 是否有效
     */
    boolean validateSession(SocialSession session);

    /**
     * 登出用户
     * @param session 用户会话
     */
    void logout(SocialSession session);

    /**
     * 获取OAuth授权URL
     * @param redirectUri 回调URI
     * @param state 状态参数（用于防止CSRF）
     * @return 授权URL
     */
    String getAuthorizationUrl(String redirectUri, String state);

    /**
     * 通过授权码获取会话
     * @param authorizationCode 授权码
     * @param redirectUri 回调URI
     * @return 用户会话
     * @throws SocialAuthenticationException 获取失败异常
     */
    SocialSession handleCallback(String authorizationCode, String redirectUri)
            throws SocialAuthenticationException;
}
