/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class LoadFlowService {

    private static final String LOAD_FLOW_API_VERSION = "v1";
    private static final String DELIMITER = "/";

    private WebClient webClient;
    private String loadFlowBaseUri;

    @Autowired
    public LoadFlowService(@Value("${backing-services.loadflow-server.base-uri:http://loadflow-server/}") String loadFlowBaseUri,
                           WebClient.Builder webClientBuilder) {
        this.webClient =  webClientBuilder.build();
        this.loadFlowBaseUri = loadFlowBaseUri;
    }

    public LoadFlowService(String loadFlowBaseUri) {
        this.webClient = WebClient.builder().build();
        this.loadFlowBaseUri = loadFlowBaseUri;
    }

    public void setBaseUri(String loadFlowBaseUri) {
        this.loadFlowBaseUri = loadFlowBaseUri;
    }

    public Mono<String> run(List<UUID> networksIds) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOAD_FLOW_API_VERSION + "/networks/{networkUuid}/run");

        for (int i = 1; i < networksIds.size(); ++i) {
            uriBuilder = uriBuilder.queryParam("networkUuid", networksIds.get(i).toString());
        }
        String uri = uriBuilder.buildAndExpand(networksIds.get(0).toString()).toUriString();

        return webClient.put()
                .uri(loadFlowBaseUri + uri)
                .retrieve()
                .bodyToMono(String.class);
    }
}
