/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.UUID;

import com.powsybl.commons.PowsyblException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ResourceUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class BalancesAdjustmentService {

    private static final String BALANCE_ADJUSTEMENT_API_VERSION = "v1";
    private static final String DELIMITER = "/";

    private WebClient webClient;

    @Autowired
    public BalancesAdjustmentService(RestTemplateBuilder builder,
                                     @Value("${backing-services.balances-adjustment-server.base-uri:http://balances-adjustment-server/}") String balanceAdjustementBaseUri,
                                     WebClient.Builder webClientBuilder) {
        this.webClient =  webClientBuilder.baseUrl(balanceAdjustementBaseUri).build();
    }

    public BalancesAdjustmentService(String loadFlowBaseUri) {
        WebClient.Builder webClientBuilder = WebClient.builder();
        this.webClient =  webClientBuilder.baseUrl(loadFlowBaseUri).build();
    }

    public Mono<String> doBalance(List<UUID> networksIds) {
        try {
            File targetNetPositionsFile = ResourceUtils.getFile("classpath:targetNetPositions.json");

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("targetNetPositionFile", new FileSystemResource(targetNetPositionsFile));

            String uri = getStringUri(networksIds, DELIMITER, BALANCE_ADJUSTEMENT_API_VERSION);

            return webClient.put()
                    .uri(uri)
                    .header(HttpHeaders.CONTENT_TYPE, String.valueOf(MediaType.MULTIPART_FORM_DATA))
                    .body(BodyInserters.fromValue(body))
                    .retrieve()
                    .bodyToMono(String.class);

        } catch (FileNotFoundException e) {
            throw new PowsyblException("No target net positions file found");
        }
    }

    static String getStringUri(List<UUID> networksIds, String delimiter, String balanceAdjustementApiVersion) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(delimiter + balanceAdjustementApiVersion + "/networks/{networkUuid}/run");
        for (int i = 1; i < networksIds.size(); ++i) {
            uriBuilder = uriBuilder.queryParam("networkUuid", networksIds.get(i).toString());
        }
        return uriBuilder.buildAndExpand(networksIds.get(0).toString()).toUriString();
    }
}
