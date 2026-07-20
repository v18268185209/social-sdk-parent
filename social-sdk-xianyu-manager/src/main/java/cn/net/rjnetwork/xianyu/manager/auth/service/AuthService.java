^package cn.net.rjnetwork.xianyu.manager.auth.service;

import cn.net.rjnetwork.xianyu.manager.auth.dto.JwtResponse;
import cn.net.rjnetwork.xianyu.manager.auth.dto.JwtResponse.AdminUserInfo;
import cn.net.rjnetwork.xianyu.manager.auth.dto.LoginRequest;
import cn.net.rjnetwork.xianyu.manager.auth.mapper.AdminUserMapper;
import cn.net.rjnetwork.xianyu.manager.auth.model.AdminUser;
import cn.net.rjnetwork.xianyu.manager.auth.security.JwtUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final AdminUserMapper adminUserMapper;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AdminUserMapper adminUserMapper, JwtUtils jwtUtils, PasswordEncoder passwordEncoder) {
        this.adminUserMapper = adminUserMapper;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<AdminUser> findByUsername(String username) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminUser::getUsername, username);
        AdminUser user = adminUserMapper.selectOne(wrapper);
        return Optional.ofNullable(user);
    }

    @Transactional
    public JwtResponse login(LoginRequest request) {
        AdminUser user = findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String token = jwtUtils.generateToken(user.getUsername());
        AdminUserInfo info = new AdminUserInfo();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setDisplayName(user.getDisplayName());
        info.setRoleLevel(user.getRoleLevel());
        return JwtResponse.of(token, jwtUtils.getExpiration(), info);
    }

    @Transactional
    public void initDefaultAdmin(String username, String password) {
        if (findByUsername(username).isPresent()) {
            return;
        }

        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName("管理员");
        user.setRoleLevel(9);
        adminUserMapper.insert(user);
    }
}
