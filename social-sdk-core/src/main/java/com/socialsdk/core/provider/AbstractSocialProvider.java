package com.socialsdk.core.provider;

import com.socialsdk.core.constant.SocialPlatform;
import com.socialsdk.core.exception.SocialAuthenticationException;
import com.socialsdk.core.exception.SocialNetworkException;
import com.socialsdk.core.model.PostResult;
import com.socialsdk.core.model.SocialContent;
import com.socialsdk.core.model.SocialSession;
import com.socialsdk.core.model.SocialUserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 社交平台提供者抽象基类
 * 提供通用功能实现
 */
public abstract class AbstractSocialProvider implements SocialProvider {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public SocialSession authenticate(Object credentials) throws SocialAuthenticationException {
        throw new SocialAuthenticationException(
                "Authentication method not implemented for platform: " + getPlatform());
    }

    @Override
    public SocialSession refreshSession(SocialSession session) throws SocialAuthenticationException {
        if (session == null || session.getRefreshToken() == null) {
            throw new SocialAuthenticationException(
                    "Cannot refresh session: no refresh token available");
        }
        throw new SocialAuthenticationException(
                "Session refresh not implemented for platform: " + getPlatform());
    }

    @Override
    public SocialUserProfile getUserProfile(SocialSession session)
            throws SocialAuthenticationException {
        throw new SocialAuthenticationException(
                "Get user profile not implemented for platform: " + getPlatform());
    }

    @Override
    public PostResult postContent(SocialSession session, SocialContent content)
            throws SocialNetworkException, SocialAuthenticationException {
        throw new SocialNetworkException(
                "Post content not implemented for platform: " + getPlatform());
    }

    @Override
    public boolean deletePost(SocialSession session, String postId)
            throws SocialAuthenticationException {
        throw new SocialAuthenticationException(
                "Delete post not implemented for platform: " + getPlatform());
    }

    @Override
    public Object getPostDetail(SocialSession session, String postId)
            throws SocialAuthenticationException {
        throw new SocialAuthenticationException(
                "Get post detail not implemented for platform: " + getPlatform());
    }

    @Override
    public Object getTimeline(SocialSession session, int limit)
            throws SocialAuthenticationException {
        throw new SocialAuthenticationException(
                "Get timeline not implemented for platform: " + getPlatform());
    }

    @Override
    public boolean validateSession(SocialSession session) {
        if (session == null) {
            return false;
        }
        return isSessionValid(session);
    }

    @Override
    public void logout(SocialSession session) {
        if (session != null) {
            session.setAccessToken(null);
            session.setRefreshToken(null);
            session.setExpiresAt(null);
            logger.info("Logged out user: {} on platform: {}",
                    session.getUserId(), getPlatform());
        }
    }

    @Override
    public String getAuthorizationUrl(String redirectUri, String state) {
        throw new UnsupportedOperationException(
                "OAuth not supported for platform: " + getPlatform());
    }

    @Override
    public SocialSession handleCallback(String authorizationCode, String redirectUri)
            throws SocialAuthenticationException {
        throw new SocialAuthenticationException(
                "OAuth callback not implemented for platform: " + getPlatform());
    }

    /**
     * 验证会话是否有效，无效则抛出异常
     */
    protected void validateSessionOrThrow(SocialSession session) throws SocialAuthenticationException {
        if (!isSessionValid(session)) {
            throw new SocialAuthenticationException("Invalid or expired session");
        }
    }

    /**
     * 检查并抛出网络异常
     */
    protected void handleNetworkError(Exception e) throws SocialNetworkException {
        logger.error("Network error occurred", e);
        throw new SocialNetworkException("Network error: " + e.getMessage(), e);
    }
}
