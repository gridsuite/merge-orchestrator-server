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
import org.gridsuite.merge.orchestrator.server.repositories.ParametersEntity;
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
@Api(value = "Merge orchestrator config processes")
@ComponentScan(basePackageClasses = MergeOrchestratorService.class)
public class ParametersConfigController {

    private final MergeOrchestratorService mergeOrchestratorService;

    public ParametersConfigController(MergeOrchestratorService mergeOrchestratorService) {
        this.mergeOrchestratorService = mergeOrchestratorService;
    }

    @GetMapping(value = "/parameters", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get all merge configurations", response = List.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of all merge configurations")})
    public ResponseEntity<List<ParametersEntity>> getMerges() {
        List<ParametersEntity> configs = mergeOrchestratorService.getParameters();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(configs);
    }

    @GetMapping(value = "/parameters/{process}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get merge configuration by process", response = List.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The merge configurations by the process")})
    public ResponseEntity<ParametersEntity> getMerges(@PathVariable String process) {
        ParametersEntity config = mergeOrchestratorService.getParametersByProcess(process).orElse(null);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(config);
    }

    @PostMapping(value = "/parameters")
    @ApiOperation(value = "add a new merge process")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The new merge process added"),
            @ApiResponse(code = 409, message = "The study already exist or the case doesn't exists")})
    public ResponseEntity<Void> createStudyFromExistingCase(@RequestBody ParametersEntity parametersEntity) {
        mergeOrchestratorService.addParameters(parametersEntity);
        return ResponseEntity.ok().build();
    }

}

