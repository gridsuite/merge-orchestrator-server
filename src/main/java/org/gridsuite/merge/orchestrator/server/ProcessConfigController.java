/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Merge orchestrator configs")
@ComponentScan(basePackageClasses = MergeOrchestratorService.class)
public class ProcessConfigController {

    private final MergeOrchestratorConfigService mergeOrchestratorConfigService;

    public ProcessConfigController(MergeOrchestratorConfigService mergeOrchestratorConfigService) {
        this.mergeOrchestratorConfigService = mergeOrchestratorConfigService;
    }

    @GetMapping(value = "/configs", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all processes configurations")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of processes configurations")})
    public ResponseEntity<List<ProcessConfig>> getConfigs() {
        List<ProcessConfig> configs = mergeOrchestratorConfigService.getConfigs();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(configs);
    }

    @GetMapping(value = "/configs/{processUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get merge configuration by process")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The merge configurations by the process")})
    public ResponseEntity<ProcessConfig> getConfigs(@PathVariable UUID processUuid) {
        ProcessConfig config = mergeOrchestratorConfigService.getConfig(processUuid).orElse(null);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(config);
    }

    @PostMapping(value = "/configs")
    @Operation(summary = "Add a new configuration for a new process")
    @ApiResponses(value = @ApiResponse(responseCode = "200", description = "The new configuration added"))
    public ResponseEntity<Void> addConfig(@RequestBody ProcessConfig processConfig) {
        mergeOrchestratorConfigService.addConfig(processConfig);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/configs/{processUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete merge configuration for a specific process")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "configuration deleted")})
    public ResponseEntity<Void> deleteConfigByProcess(@PathVariable UUID processUuid) {
        mergeOrchestratorConfigService.deleteConfig(processUuid);
        return ResponseEntity.ok().build();
    }
}

