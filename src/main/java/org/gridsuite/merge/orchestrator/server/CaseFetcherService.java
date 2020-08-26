/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.gridsuite.merge.orchestrator.server.dto.CaseInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import com.powsybl.cases.datasource.CaseDataSourceClient;
import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.import_.Importer;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class CaseFetcherService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaseFetcherService.class);

    private static final String CASE_API_VERSION = "v1";
    private static final String DELIMITER = "/";

    private RestTemplate caseServerRest;

    @Autowired
    public CaseFetcherService(RestTemplateBuilder builder,
            @Value("${backing-services.case-server.base-uri:http://case-server/}") String caseServerBaseUri) {
        this.caseServerRest = builder.uriTemplateHandler(new DefaultUriBuilderFactory(caseServerBaseUri))
                .build();
    }

    public CaseFetcherService(RestTemplate restTemplate) {
        this.caseServerRest = restTemplate;
    }

    private String getDateSearchTerm(ZonedDateTime dateTime) {
        String formattedDate = dateTime.format(DateTimeFormatter.ISO_DATE_TIME);
        try {
            return "date:\"" + URLEncoder.encode(formattedDate, "UTF-8") + "\"";
        } catch (UnsupportedEncodingException e) {
            throw new PowsyblException("Error when decoding the query string");
        }
    }

    public List<CaseInfos> getCases(List<String> tsos, ZonedDateTime dateTime) {
        // construct the search query
        StringBuilder query = new StringBuilder();
        query.append(getDateSearchTerm(dateTime)).append(" AND geographicalCode:(");
        for (int i = 0; i < tsos.size() - 1; ++i) {
            query.append(tsos.get(i))
                    .append(" OR ");
        }
        if (!tsos.isEmpty()) {
            query.append(tsos.get(tsos.size() - 1));
        }
        query.append(")");

        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/search")
                .queryParam("q", query.toString())
                .toUriString();
        try {
            ResponseEntity<List<Map<String, String>>> responseEntity = caseServerRest.exchange(path, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() { });
            return responseEntity.getBody().stream().map(c -> new CaseInfos(c.get("name"), UUID.fromString(c.get("uuid")), c.get("format"))).collect(Collectors.toList());
        } catch (HttpStatusCodeException e) {
            LOGGER.error("Error searching cases: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    public Network getCase(UUID caseUuid) {
        CaseDataSourceClient dataSource = new CaseDataSourceClient(caseServerRest, caseUuid);
        Importer importer = Importers.findImporter(dataSource, LocalComputationManager.getDefault());
        if (importer == null) {
            throw new PowsyblException("No importer found");
        }
        Network network = null;
        try {
            network = importer.importData(dataSource, NetworkFactory.findDefault(), null);
        } catch (Exception e) {
            LOGGER.error("Error fetching case: {}", e.getMessage());
        }
        return network;
    }
}
