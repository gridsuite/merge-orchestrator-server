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
public class IgmQualityCheckService {

    private static final String CASE_VALIDATION_API_VERSION = "v1";

    private RestTemplate caseValidationServerRest;

    @Autowired
    public IgmQualityCheckService(RestTemplateBuilder builder,
                                  @Value("${backing-services.case-validation-server.base-uri:http://case-validation-server/}") String caseValidationBaseUri) {
        this.caseValidationServerRest = builder.uriTemplateHandler(new DefaultUriBuilderFactory(caseValidationBaseUri)).build();
    }

    public IgmQualityCheckService(RestTemplate restTemplate) {
        this.caseValidationServerRest = restTemplate;
    }

    public boolean check(UUID networkUuid) {
        boolean res = false;
        try {
            ResponseEntity<String> response = caseValidationServerRest.exchange(CASE_VALIDATION_API_VERSION + "/networks/{networkUuid}/validate",
                    HttpMethod.PUT,
                    null,
                    String.class,
                    networkUuid.toString());
            JsonNode node = new ObjectMapper().readTree(response.getBody()).path("loadFlowOk");
            if (!node.isMissingNode()) {
                res = node.asBoolean();
            }
        } catch (JsonProcessingException e) {
            throw new PowsyblException("Error parsing case validation result");
        }
        return res;
    }
}
