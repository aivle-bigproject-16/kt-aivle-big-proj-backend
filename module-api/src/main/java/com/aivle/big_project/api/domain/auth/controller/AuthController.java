package com.aivle.big_project.api.domain.auth.controller;

import com.aivle.big_project.api.domain.auth.dto.EmailSendRequest;
import com.aivle.big_project.api.domain.auth.dto.EmailVerifyRequest;
import com.aivle.big_project.api.domain.auth.dto.LoginRequest;
import com.aivle.big_project.api.domain.auth.dto.SignUpRequest;
import com.aivle.big_project.api.domain.auth.dto.UserResponse;
import com.aivle.big_project.api.domain.auth.service.AuthService;
import com.aivle.big_project.api.domain.auth.service.EmailService;
import com.aivle.big_project.api.domain.auth.dto.LoginResponseData;
import com.aivle.big_project.api.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;

    @PostMapping("/email/send")
    public ResponseEntity<Void> sendEmailCode(@Valid @RequestBody EmailSendRequest request) {
        emailService.sendVerificationCode(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email/verify")
    public ResponseEntity<String> verifyEmailCode(@Valid @RequestBody EmailVerifyRequest request) {
        boolean verified = emailService.verifyCode(request.email(), request.code());
        if (verified) {
            return ResponseEntity.ok("인증이 완료되었습니다.");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("인증번호가 일치하지 않거나 만료되었습니다.");
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignUpRequest request) {
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("회원가입이 완료되었습니다.", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseData>> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        String token = authService.login(request);
        UserResponse userProfile = authService.getProfile(request.email());

        ResponseCookie cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(3600)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        LoginResponseData responseData = new LoginResponseData(
                userProfile.name(),
                userProfile.role() != null ? userProfile.role().name() : null
        );

        return ResponseEntity.ok(ApiResponse.success("로그인이 완료되었습니다.", responseData));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserResponse userProfile = authService.getProfile(userDetails.getUsername());
        return ResponseEntity.ok(userProfile);
    }
}

