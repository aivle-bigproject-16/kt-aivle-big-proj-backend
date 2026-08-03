package com.aivle.big_project.api.domain.simulation.client;

import com.aivle.big_project.api.domain.simulation.client.dto.AiServerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AiGatewayClient {

    private final RestClient aiGatewayRestClient;

    public AiServerDto.AcceptedResponse requestCellAnalysis(
            AiServerDto.CellAnalysisRequest request
    ) {
        return aiGatewayRestClient.post()
                .uri("/ai/cells/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AiServerDto.AcceptedResponse.class);
    }
}
