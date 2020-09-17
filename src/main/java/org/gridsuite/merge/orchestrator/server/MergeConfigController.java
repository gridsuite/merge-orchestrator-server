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
import org.gridsuite.merge.orchestrator.server.repositories.ProcessConfigEntity;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author Chamseddine Benhamed <Chamseddine.Benhamed at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + MergeOrchestratorApi.API_VERSION)
@Transactional
@Api(value = "Merge orchestrator parameters")
@ComponentScan(basePackageClasses = MergeOrchestratorService.class)
public class MergeConfigController {

    private final MergeOrchestratorConfigService mergeOrchestratorConfigService;

    public MergeConfigController(MergeOrchestratorConfigService mergeOrchestratorConfigService) {
        this.mergeOrchestratorConfigService = mergeOrchestratorConfigService;
    }

    @GetMapping(value = "/configs", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get all processes configurations", response = List.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of processes configurations")})
    public ResponseEntity<List<ProcessConfigEntity>> getConfigs() {
        List<ProcessConfigEntity> configs = mergeOrchestratorConfigService.getConfigs();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(configs);
    }

    @GetMapping(value = "/configs/{process}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get merge configuration by process", response = List.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The merge configurations by the process")})
    public ResponseEntity<ProcessConfigEntity> getConfigs(@PathVariable String process) {
        ProcessConfigEntity config = mergeOrchestratorConfigService.getConfig(process).orElse(null);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(config);
    }

    @PostMapping(value = "/configs")
    @ApiOperation(value = "Add a new configuration for a new process")
    @ApiResponses(value = @ApiResponse(code = 200, message = "The new configuration added"))
    public ResponseEntity<Void> createStudyFromExistingCase(@RequestBody ProcessConfigEntity processConfigEntity) {
        mergeOrchestratorConfigService.addConfig(processConfigEntity);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/configs/{process}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Delete merge configuration for a specific process")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "configuration deleted")})
    public ResponseEntity<Void> deleteConfigByProcess(@PathVariable String process) {
        mergeOrchestratorConfigService.deleteConfig(process);
        return ResponseEntity.ok().build();
    }

}

