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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class BalancesAdjustmentService {

    private static final String BALANCE_ADJUSTEMENT_API_VERSION = "v1";
    private static final String DELIMITER = "/";

    private RestTemplate balancesAdjustmentServerRest;

    @Autowired
    public BalancesAdjustmentService(RestTemplateBuilder builder,
            @Value("${gridsuite.services.balances-adjustment-server.base-uri:http://balances-adjustment-server/}") String balanceAdjustementBaseUri) {
        this.balancesAdjustmentServerRest = builder.uriTemplateHandler(
                new DefaultUriBuilderFactory(balanceAdjustementBaseUri)).build();
    }

    public BalancesAdjustmentService(RestTemplate restTemplate) {
        this.balancesAdjustmentServerRest = restTemplate;
    }

    public String doBalance(List<UUID> networksIds) {
        try {
            File targetNetPositionsFile = ResourceUtils.getFile("classpath:targetNetPositions.json");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("balanceComputationParamsFile", null);
            body.add("targetNetPositionFile", new FileSystemResource(targetNetPositionsFile));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder
                    .fromPath(DELIMITER + BALANCE_ADJUSTEMENT_API_VERSION + "/networks/{networkUuid}/run");
            for (int i = 1; i < networksIds.size(); ++i) {
                uriBuilder = uriBuilder.queryParam("networkUuid", networksIds.get(i).toString());
            }
            String uri = uriBuilder.build().toUriString();

            ResponseEntity<String> res = balancesAdjustmentServerRest.exchange(uri,
                    HttpMethod.PUT,
                    requestEntity,
                    String.class,
                    networksIds.get(0).toString());
            return res.getBody();
        } catch (FileNotFoundException e) {
            throw new PowsyblException("No target net positions file found");
        }
    }
}
