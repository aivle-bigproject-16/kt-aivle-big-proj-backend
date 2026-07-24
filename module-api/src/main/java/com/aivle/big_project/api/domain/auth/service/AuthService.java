package com.aivle.big_project.api.domain.auth.service;

import com.aivle.big_project.api.domain.auth.dto.LoginRequest;
import com.aivle.big_project.api.domain.auth.dto.SignUpRequest;
import com.aivle.big_project.api.domain.auth.dto.UserResponse;
import com.aivle.big_project.api.global.security.JwtProvider;
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
    private final EmailService emailService;

    @Transactional
    public UserResponse signup(SignUpRequest request) {
        if (!emailService.isEmailVerified(request.email())) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다.");
        }

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .role(Role.INSPECTOR)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        
        emailService.clearVerification(request.email());

        return convertToResponse(savedUser);
    }

    public String login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
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

