/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.cases.datasource.CaseDataSourceClient;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.merge.orchestrator.server.dto.CaseInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
@ComponentScan(basePackageClasses = { NetworkStoreService.class })
public class CaseFetcherService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaseFetcherService.class);

    private static final String CASE_API_VERSION = "v1";
    private static final String DELIMITER = "/";

    private RestTemplate caseServerRest;

    private NetworkStoreService networkStoreService;

    @Autowired
    public CaseFetcherService(NetworkStoreService networkStoreService,
                              RestTemplateBuilder builder,
                              @Value("${backing-services.case-server.base-uri:http://case-server/}") String caseServerBaseUri) {
        this.networkStoreService = networkStoreService;
        this.caseServerRest = builder.uriTemplateHandler(new DefaultUriBuilderFactory(caseServerBaseUri))
                .build();
    }

    public CaseFetcherService(RestTemplate restTemplate) {
        this.caseServerRest = restTemplate;
    }

    private String getSearchQuery(List<String> tsos, ZonedDateTime dateTime, String format, String businessProcess) {
        StringBuilder query = new StringBuilder();
        String formattedDate = dateTime.format(DateTimeFormatter.ISO_DATE_TIME);
        try {
            query.append("date:\"" + URLEncoder.encode(formattedDate, "UTF-8") + "\"")
                    .append(" AND tso:")
                    .append(tsos.stream().collect(Collectors.joining(" OR ", "(", ")")))
                    .append(" AND format:")
                    .append(format)
                    .append(" AND businessProcess:")
                    .append(businessProcess);
        } catch (UnsupportedEncodingException e) {
            throw new PowsyblException(String.format("Error when encoding the date query string : %s", formattedDate));
        }
        return query.toString();
    }

    public List<CaseInfos> getCases(List<String> tsos, ZonedDateTime dateTime, String format, String businessProcess) {
        String uri = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/search")
                .queryParam("q", getSearchQuery(tsos, dateTime, format, businessProcess)).build().toUriString();

        try {
            ResponseEntity<List<Map<String, String>>> responseEntity = caseServerRest.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() { });
            List<Map<String, String>> body = responseEntity.getBody();
            if (body != null) {
                return body.stream().map(c -> new CaseInfos(c.get("name"),
                        UUID.fromString(c.get("uuid")),
                        c.get("format"),
                        c.get("tso"),
                        c.get("businessProcess")))
                        .collect(Collectors.toList());
            } else {
                LOGGER.error("Error searching cases: body is null {}", responseEntity);
            }
        } catch (HttpStatusCodeException e) {
            LOGGER.error("Error searching cases: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    public UUID importCase(UUID caseUuid) {
        CaseDataSourceClient dataSource = new CaseDataSourceClient(caseServerRest, caseUuid);
        Network network = networkStoreService.importNetwork(dataSource);
        return networkStoreService.getNetworkUuid(network);
    }
}
