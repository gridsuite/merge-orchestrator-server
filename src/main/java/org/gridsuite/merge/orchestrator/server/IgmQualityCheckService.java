/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class IgmQualityCheckService {

    private static final String CASE_VALIDATION_API_VERSION = "v1";

    private WebClient webClient;

    @Autowired
    public IgmQualityCheckService(@Value("${backing-services.case-validation-server.base-uri:http://case-validation-server/}") String caseValidationBaseUri,
                                  WebClient.Builder webClientBuilder) {
        this.webClient =  webClientBuilder.baseUrl(caseValidationBaseUri).build();
    }

    public IgmQualityCheckService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<Boolean> check(UUID networkUuid) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(CASE_VALIDATION_API_VERSION + "/networks/{networkUuid}/validate");
        String uri = uriBuilder.buildAndExpand(networkUuid.toString()).toUriString();

        Mono<String> stringMono =  webClient.put()
                .uri(uri)
                .body(null)
                .retrieve()
                .bodyToMono(String.class);

        return stringMono.flatMap(str -> {
            boolean res = false;
            JsonNode node = null;
            try {
                node = new ObjectMapper().readTree(str).path("loadFlowOk");
            } catch (JsonProcessingException e) {
                return Mono.error(new PowsyblException("Error parsing case validation result"));
            }
            if (!node.isMissingNode()) {
                res = node.asBoolean();
            }
            return Mono.just(res);
        });
    }
}
