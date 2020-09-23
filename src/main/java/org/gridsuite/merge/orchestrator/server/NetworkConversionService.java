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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com
 */
@Service
public class NetworkConversionService {
    private static final String NETWORK_CONVERSION_API_VERSION = "v1";
    private static final String DELIMITER = "/";

    private RestTemplate networkConversionServerRest;

    @Autowired
    public NetworkConversionService(RestTemplateBuilder builder,
                                    @Value("${backing-services.network-conversion.base-uri:http://network-conversion-server/}") String networkConversionBaseUri) {
        this.networkConversionServerRest = builder.uriTemplateHandler(
                new DefaultUriBuilderFactory(networkConversionBaseUri)
        ).build();
    }

    public NetworkConversionService(RestTemplate restTemplate) {
        this.networkConversionServerRest = restTemplate;
    }

    public byte[] exportMerge(List<UUID> networksIds, String format) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/export/{format}");
        for (int i = 1; i < networksIds.size(); ++i) {
            uriBuilder = uriBuilder.queryParam("networkUuid", networksIds.get(i).toString());
        }
        String uri = uriBuilder.build().toUriString();

        ResponseEntity<byte[]> responseEntity = networkConversionServerRest.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() { }, networksIds.get(0).toString(), format);
        return responseEntity.getBody();
    }
}
