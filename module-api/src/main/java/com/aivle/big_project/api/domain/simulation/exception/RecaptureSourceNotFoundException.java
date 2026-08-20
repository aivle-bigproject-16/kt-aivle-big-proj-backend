package com.aivle.big_project.api.domain.simulation.exception;

/**
 * 지정된 재촬영 회차의 원본 이미지가 없어 재시도로 복구할 수 없을 때 발생합니다.
 */
public class RecaptureSourceNotFoundException extends RuntimeException {

    public RecaptureSourceNotFoundException(String message) {
        super(message);
    }
}
