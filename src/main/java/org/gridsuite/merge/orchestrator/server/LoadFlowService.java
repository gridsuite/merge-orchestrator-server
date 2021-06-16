/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;
import org.gridsuite.merge.orchestrator.server.dto.MergeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class LoadFlowService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowService.class);

    private static final String LOAD_FLOW_API_VERSION = "v1";
    private static final String DELIMITER = "/";

    private enum Step {
        FIRST("First"),
        SECOND("Second"),
        THIRD("Third");

        private String value;

        Step(final String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

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

    private boolean hasMainComponentConverged(LoadFlowResult result) {
        if (result == null || result.getComponentResults().isEmpty()) {
            return false;
        }
        return result.getComponentResults().get(0).getStatus() == LoadFlowResult.ComponentResult.Status.CONVERGED;
    }

    private boolean stepRun(Step step, LoadFlowParameters params, String uri, List<UUID> networksIds) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var baos = new ByteArrayOutputStream();
        JsonLoadFlowParameters.write(params, baos);
        HttpEntity<byte[]> requestEntity = new HttpEntity<>(baos.toByteArray(), headers);

        LoadFlowResult result = loadFlowServerRest.exchange(uri,
            HttpMethod.PUT,
            requestEntity,
            LoadFlowResult.class,
            networksIds.get(0).toString()).getBody();

        boolean isLoadFlowOk = hasMainComponentConverged(result);
        if (!isLoadFlowOk) {
            String message = step + " loadflow failed with parameters : " + params;
            if (step != Step.THIRD) {
                LOGGER.warn(message);
            } else {
                LOGGER.error(message);
            }
        }

        return isLoadFlowOk;
    }

    public MergeStatus run(List<UUID> networksIds, UUID report) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOAD_FLOW_API_VERSION + "/networks/{networkUuid}/run");
        for (int i = 1; i < networksIds.size(); ++i) {
            uriBuilder = uriBuilder.queryParam("networkUuid", networksIds.get(i).toString());
        }
        uriBuilder = uriBuilder.queryParam(MergeOrchestratorConstants.REPORT_ID, report.toString());
        uriBuilder = uriBuilder.queryParam(MergeOrchestratorConstants.REPORT_NAME, Step.FIRST.value + "Loadflow");
        String uri = uriBuilder.build().toUriString();

        // first run with initial settings
        LoadFlowParameters params = new LoadFlowParameters()
            .setTransformerVoltageControlOn(true)
            .setSimulShunt(true)
            .setDistributedSlack(true)
            .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD)
            .setReadSlackBus(true)
            .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        if (stepRun(Step.FIRST, params, uri, networksIds)) {
            return MergeStatus.FIRST_LOADFLOW_SUCCEED;
        }

        uriBuilder = uriBuilder.replaceQueryParam(MergeOrchestratorConstants.REPORT_NAME, Step.SECOND.value + "Loadflow");
        uri = uriBuilder.build().toUriString();

        // second run : disabling transformer tap and switched shunt adjustment
        params.setTransformerVoltageControlOn(false);
        params.setSimulShunt(false);
        if (stepRun(Step.SECOND, params, uri, networksIds)) {
            return MergeStatus.SECOND_LOADFLOW_SUCCEED;
        }

        uriBuilder = uriBuilder.replaceQueryParam(MergeOrchestratorConstants.REPORT_NAME, Step.THIRD.value + "Loadflow");
        uri = uriBuilder.build().toUriString();

        // third run : relaxing reactive power limits
        params.setNoGeneratorReactiveLimits(true);
        return stepRun(Step.THIRD, params, uri, networksIds) ? MergeStatus.THIRD_LOADFLOW_SUCCEED : MergeStatus.LOADFLOW_FAILED;
    }
}
