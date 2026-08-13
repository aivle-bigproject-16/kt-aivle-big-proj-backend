package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.SnapshotResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SimulationSnapshotStore {

    private static final String SNAPSHOT_KEY = "simulation:current";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(SnapshotResponse snapshot) {
        try {
            String snapshotJson = objectMapper.writeValueAsString(snapshot);

            redisTemplate.opsForValue().set(
                    SNAPSHOT_KEY,
                    snapshotJson
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "시뮬레이션 스냅샷 저장에 실패했습니다.",
                    exception
            );
        } catch (RedisConnectionFailureException exception) {
            throw redisUnavailable(exception);
        }
    }

    public Optional<SnapshotResponse> find() {
        try {
            String snapshotJson = redisTemplate
                    .opsForValue()
                    .get(SNAPSHOT_KEY);

            if (snapshotJson == null) {
                return Optional.empty();
            }

            return Optional.of(
                    objectMapper.readValue(
                            snapshotJson,
                            SnapshotResponse.class
                    )
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "시뮬레이션 스냅샷 조회에 실패했습니다.",
                    exception
            );
        } catch (RedisConnectionFailureException exception) {
            throw redisUnavailable(exception);
        }
    }

    private ResponseStatusException redisUnavailable(
            RedisConnectionFailureException exception
    ) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "시뮬레이션 상태 저장소에 연결할 수 없습니다.",
                exception
        );
    }
}
