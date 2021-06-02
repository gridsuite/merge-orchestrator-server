/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.merge.orchestrator.server.dto.BoundaryContent;
import org.gridsuite.merge.orchestrator.server.dto.FileInfos;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
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

    public List<BoundaryContent> getLastBoundaries() {
        List<BoundaryContent> lastBoundaries = new ArrayList<>();
        String uri = DELIMITER + CGMES_BOUNDARY_API_VERSION + "/boundaries/last";
        try {
            ResponseEntity<List<Map<String, String>>> responseEntity = cgmesBoundaryServerRest.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<List<Map<String, String>>>() {
            });
            List<Map<String, String>> body = responseEntity.getBody();
            if (body != null) {
                lastBoundaries = body.stream().map(c -> new BoundaryContent(c.get(ID_KEY),
                    c.get(FILE_NAME_KEY),
                    c.get(BOUNDARY_KEY)))
                    .collect(Collectors.toList());
            } else {
                LOGGER.error("Error searching boundaries: body is null {}", responseEntity);
            }
        } catch (RestClientException e) {
            LOGGER.error("Error searching boundaries: {}", e.getMessage());
        }
        return lastBoundaries;
    }

    public Optional<BoundaryContent> getBoundary(String boundaryId) {
        String uri = DELIMITER + CGMES_BOUNDARY_API_VERSION + "/boundaries/" + boundaryId;
        try {
            ResponseEntity<Map<String, String>> responseEntity = cgmesBoundaryServerRest.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() {
            });
            Map<String, String> body = responseEntity.getBody();
            if (body != null) {
                return Optional.of(new BoundaryContent(body.get(ID_KEY), body.get(FILE_NAME_KEY), body.get(BOUNDARY_KEY)));
            } else {
                LOGGER.error("Error searching boundary with id {} : body is null {}", boundaryId, responseEntity);
            }
        } catch (RestClientException e) {
            LOGGER.error("Error searching boundary with id {} : {}", boundaryId, e.getMessage());
        }
        return Optional.empty();
    }

    public static List<FileInfos> getFileInfosBoundaries(List<BoundaryContent> boundaryInfos) {
        List<FileInfos> boundaries = new ArrayList<>();
        for (BoundaryContent boundary : boundaryInfos) {
            boundaries.add(new FileInfos(boundary.getFilename(), boundary.getBoundary().getBytes(StandardCharsets.UTF_8)));
        }
        return boundaries;
    }
}
