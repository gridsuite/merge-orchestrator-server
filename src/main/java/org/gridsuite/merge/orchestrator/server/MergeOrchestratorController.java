/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.commons.PowsyblException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.gridsuite.merge.orchestrator.server.dto.MergeInfos;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Optional;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + MergeOrchestratorApi.API_VERSION)
@Transactional
@Api(value = "Merge orchestrator server")
@ComponentScan(basePackageClasses = MergeOrchestratorService.class)
public class MergeOrchestratorController {

    private final MergeOrchestratorService mergeOrchestratorService;

    public MergeOrchestratorController(MergeOrchestratorService mergeOrchestratorService) {
        this.mergeOrchestratorService = mergeOrchestratorService;
    }

    @GetMapping(value = "/merges", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get all merges", response = List.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of all merges")})
    public ResponseEntity<List<MergeInfos>> getMergesList() {
        List<MergeInfos> merges = mergeOrchestratorService.getMergesList();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(merges);
    }

    @GetMapping(value = "/merges/{process}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get all merges for a process", response = List.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of all merges for a process")})
    public ResponseEntity<List<MergeInfos>> getProcessMergesList(@PathVariable("process") String process) {
        List<MergeInfos> merges = mergeOrchestratorService.getProcessMergesList(process);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(merges);
    }

    @GetMapping(value = "/merges/{process}/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "get a merge by process and date", response = MergeInfos.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The merge information"),
            @ApiResponse(code = 404, message = "The merge doesn't exist")})
    public ResponseEntity<MergeInfos> getMerge(@PathVariable("process") String process,
                                               @PathVariable("date") String date) {
        try {
            String decodedDate = URLDecoder.decode(date, "UTF-8");
            Optional<MergeInfos> merge = mergeOrchestratorService.getMerge(process, decodedDate);
            return ResponseEntity.of(merge);
        } catch (UnsupportedEncodingException e) {
            throw new PowsyblException("Error parsing date");
        }
    }
}

