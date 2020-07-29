/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

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

    public String run(UUID networkUuid) {
        ResponseEntity<String> res = loadFlowServerRest.exchange(LOAD_FLOW_API_VERSION + "/networks/{networkUuid}/run",
                HttpMethod.PUT,
                null,
                String.class,
                networkUuid.toString());
        return res.getBody();
    }
}
