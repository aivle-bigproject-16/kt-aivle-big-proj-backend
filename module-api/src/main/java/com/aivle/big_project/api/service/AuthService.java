package com.aivle.big_project.api.service;

import com.aivle.big_project.api.dto.LoginRequest;
import com.aivle.big_project.api.dto.SignUpRequest;
import com.aivle.big_project.api.dto.UserResponse;
import com.aivle.big_project.api.security.JwtProvider;
import com.aivle.big_project.domain.user.Role;
import com.aivle.big_project.domain.user.User;
import com.aivle.big_project.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public UserResponse signup(SignUpRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(Role.INSPECTOR)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);

        return convertToResponse(savedUser);
    }

    public String login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        if (!user.isActive()) {
            throw new IllegalArgumentException("비활성화된 계정입니다.");
        }

        return jwtProvider.generateToken(user.getEmail());
    }

    public UserResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        return convertToResponse(user);
    }

    private UserResponse convertToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .build();
    }
}
