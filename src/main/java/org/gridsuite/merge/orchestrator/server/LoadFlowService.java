/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class LoadFlowService {

    private static final String LOAD_FLOW_API_VERSION = "v1";

    private RestTemplate loadFlowServerRest;

    @Autowired
    public LoadFlowService(RestTemplateBuilder builder,
                           @Value("${backing-services.loadflow-server.base-uri:http://loadflow-server/}") String loadFlowBaseUri) {
        this.loadFlowServerRest = builder.uriTemplateHandler(
                new DefaultUriBuilderFactory(loadFlowBaseUri)
        ).build();
    }

    public LoadFlowService(RestTemplate restTemplate) {
        this.loadFlowServerRest = restTemplate;
    }

    public Boolean run(UUID networkUuid) {
        try {
            ResponseEntity<String> result = loadFlowServerRest.exchange(LOAD_FLOW_API_VERSION + "/networks/{networkUuid}/run",
                    HttpMethod.PUT,
                    null,
                    String.class,
                    networkUuid.toString());

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            JsonNode jsonTree = objectMapper.readTree(result.getBody());
            if (jsonTree.hasNonNull("status")) {
                return Boolean.parseBoolean(jsonTree.get("status").asText());
            }
        } catch (JsonProcessingException e) {
            throw new PowsyblException("Error parsing loadflow result");
        }
        return null;
    }
}
