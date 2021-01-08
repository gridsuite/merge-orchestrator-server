/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.merge.orchestrator.server.dto.BoundaryInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
@ComponentScan(basePackageClasses = { NetworkStoreService.class })
public class CgmesBoundaryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CgmesBoundaryService.class);

    private static final String CGMES_BOUNDARY_API_VERSION = "v1";
    private static final String DELIMITER = "/";
    private static final String ID_KEY = "id";
    private static final String FILE_NAME_KEY = "filename";
    private static final String BOUNDARY_KEY = "boundary";

    private RestTemplate cgmesBoundaryServerRest;

    @Autowired
    public CgmesBoundaryService(RestTemplateBuilder builder,
                              @Value("${backing-services.cgmes-boundary-server.base-uri:http://cgmes-boundary-server/}") String cgmesBoundaryServerBaseUri) {
        this.cgmesBoundaryServerRest = builder.uriTemplateHandler(new DefaultUriBuilderFactory(cgmesBoundaryServerBaseUri))
                .build();
    }

    public CgmesBoundaryService(RestTemplate restTemplate) {
        this.cgmesBoundaryServerRest = restTemplate;
    }

    public List<BoundaryInfos> getBoundaries() {
        String uri = UriComponentsBuilder.fromPath(DELIMITER + CGMES_BOUNDARY_API_VERSION + "/boundaries/last").build().toUriString();

        try {
            ResponseEntity<List<Map<String, String>>> responseEntity = cgmesBoundaryServerRest.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<List<Map<String, String>>>() { });
            List<Map<String, String>> body = responseEntity.getBody();
            if (body != null) {
                return body.stream().map(c -> new BoundaryInfos(c.get(ID_KEY),
                        c.get(FILE_NAME_KEY),
                        c.get(BOUNDARY_KEY)))
                        .collect(Collectors.toList());
            } else {
                LOGGER.error("Error searching boundaries: body is null {}", responseEntity);
            }
        } catch (HttpStatusCodeException e) {
            LOGGER.error("Error searching boundaries: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

}
