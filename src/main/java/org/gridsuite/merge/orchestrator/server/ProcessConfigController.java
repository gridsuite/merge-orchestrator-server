/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.gridsuite.merge.orchestrator.server.dto.ProcessConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * @author Chamseddine Benhamed <Chamseddine.Benhamed at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + MergeOrchestratorApi.API_VERSION)
@Transactional
@Api(value = "Merge orchestrator configs")
@ComponentScan(basePackageClasses = MergeOrchestratorService.class)
public class ProcessConfigController {

    private final MergeOrchestratorConfigService mergeOrchestratorConfigService;

    public ProcessConfigController(MergeOrchestratorConfigService mergeOrchestratorConfigService) {
        this.mergeOrchestratorConfigService = mergeOrchestratorConfigService;
    }

    @GetMapping(value = "/configs", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get all processes configurations", response = List.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of processes configurations")})
    public ResponseEntity<List<ProcessConfig>> getConfigs() {
        List<ProcessConfig> configs = mergeOrchestratorConfigService.getConfigs();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(configs);
    }

    @GetMapping(value = "/configs/{processUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get merge configuration by process", response = List.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The merge configurations by the process")})
    public ResponseEntity<ProcessConfig> getConfigs(@PathVariable UUID processUuid) {
        ProcessConfig config = mergeOrchestratorConfigService.getConfig(processUuid).orElse(null);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(config);
    }

    @PostMapping(value = "/configs")
    @ApiOperation(value = "Add a new configuration for a new process")
    @ApiResponses(value = @ApiResponse(code = 200, message = "The new configuration added"))
    public ResponseEntity<Void> addConfig(@RequestBody ProcessConfig processConfig) {
        mergeOrchestratorConfigService.addConfig(processConfig);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/configs/{processUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Delete merge configuration for a specific process")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "configuration deleted")})
    public ResponseEntity<Void> deleteConfigByProcess(@PathVariable UUID processUuid) {
        mergeOrchestratorConfigService.deleteConfig(processUuid);
        return ResponseEntity.ok().build();
    }
}

