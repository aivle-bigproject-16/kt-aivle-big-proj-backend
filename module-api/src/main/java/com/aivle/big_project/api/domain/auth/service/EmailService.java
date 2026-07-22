package com.aivle.big_project.api.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final StringRedisTemplate redisTemplate;
    private final org.springframework.mail.javamail.JavaMailSender mailSender;

    private static final String CODE_PREFIX = "email_code:";
    private static final String VERIFIED_PREFIX = "email_verified:";
    private static final int CODE_TTL_MINUTES = 3;
    private static final int VERIFIED_TTL_MINUTES = 30;

    public void sendVerificationCode(String email) {
        String code = generateRandomCode();
        
        // Save to Redis
        redisTemplate.opsForValue().set(CODE_PREFIX + email, code, Duration.ofMinutes(CODE_TTL_MINUTES));
        
        try {
            org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
            message.setTo(email);
            message.setSubject("[회원가입] 이메일 인증 번호입니다.");
            message.setText("인증 번호: " + code + "\n\n3분 내에 입력해주세요.");
            mailSender.send(message);
            log.info("Real email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send email to {}", email, e);
            throw new IllegalArgumentException("이메일 발송에 실패했습니다. 메일 주소를 확인해주세요.");
        }
    }

    public boolean verifyCode(String email, String code) {
        String savedCode = redisTemplate.opsForValue().get(CODE_PREFIX + email);
        
        if (savedCode != null && savedCode.equals(code)) {
            // Delete the code and mark as verified
            redisTemplate.delete(CODE_PREFIX + email);
            redisTemplate.opsForValue().set(VERIFIED_PREFIX + email, "TRUE", Duration.ofMinutes(VERIFIED_TTL_MINUTES));
            return true;
        }
        return false;
    }

    public boolean isEmailVerified(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(VERIFIED_PREFIX + email));
    }

    public void clearVerification(String email) {
        redisTemplate.delete(VERIFIED_PREFIX + email);
    }

    private String generateRandomCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}
