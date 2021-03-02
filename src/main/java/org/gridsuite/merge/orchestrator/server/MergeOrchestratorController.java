/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import io.swagger.annotations.*;
import org.gridsuite.merge.orchestrator.server.dto.FileInfos;
import org.gridsuite.merge.orchestrator.server.dto.IgmReplacingInfo;
import org.gridsuite.merge.orchestrator.server.dto.Merge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + MergeOrchestratorApi.API_VERSION)
@Transactional
@Api(value = "Merge orchestrator server")
@ComponentScan(basePackageClasses = MergeOrchestratorService.class)
public class MergeOrchestratorController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeOrchestratorController.class);

    private final MergeOrchestratorService mergeOrchestratorService;

    public MergeOrchestratorController(MergeOrchestratorService mergeOrchestratorService) {
        this.mergeOrchestratorService = mergeOrchestratorService;
    }

    @GetMapping(value = "{process}/merges", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get merges for a process", response = List.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of all merges for a process")})
    public ResponseEntity<List<Merge>> getMerges(@PathVariable("process") String process,
                                                 @RequestParam(value = "minDate", required = false) String minDate,
                                                 @RequestParam(value = "maxDate", required = false) String maxDate) {
        List<Merge> merges;
        if (minDate != null && maxDate != null) {
            String decodedMinDate = URLDecoder.decode(minDate, StandardCharsets.UTF_8);
            ZonedDateTime minDateTime = ZonedDateTime.parse(decodedMinDate);
            String decodedMaxDate = URLDecoder.decode(maxDate, StandardCharsets.UTF_8);
            ZonedDateTime maxDateTime = ZonedDateTime.parse(decodedMaxDate);
            merges = mergeOrchestratorService.getMerges(process, minDateTime, maxDateTime);
        } else {
            merges = mergeOrchestratorService.getMerges(process);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(merges);
    }

    @GetMapping(value = "{process}/{date}/export/{format}")
    @ApiOperation(value = "Export a merge from the network-store")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The export merge for process")})
    public ResponseEntity<byte[]> exportNetwork(@ApiParam(value = "Process name") @PathVariable("process") String process,
                                                @ApiParam(value = "Process date") @PathVariable("date") String date,
                                                @ApiParam(value = "Export format")@PathVariable("format") String format,
                                                @RequestParam(value = "timeZoneOffset", required = false) String timeZoneOffset) {
        LOGGER.debug("Exporting merge for process {} : {}", process, date);
        String decodedDate = URLDecoder.decode(date, StandardCharsets.UTF_8);
        ZonedDateTime dateTime = ZonedDateTime.parse(decodedDate);

        FileInfos exportedMergeInfo = mergeOrchestratorService.exportMerge(process, dateTime, format, timeZoneOffset);

        HttpHeaders header = new HttpHeaders();
        header.setContentDisposition(ContentDisposition.builder("attachment").filename(exportedMergeInfo.getName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(header).contentType(MediaType.APPLICATION_OCTET_STREAM).body(exportedMergeInfo.getData());
    }

    @PutMapping(value = "{process}/{date}/replace-igms")
    @ApiOperation(value = "Replace missing or invalid igms")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "IGMs replaced")})
    public ResponseEntity<Map<String, IgmReplacingInfo>> replaceIGMs(@ApiParam(value = "Process name") @PathVariable("process") String processName,
                                            @ApiParam(value = "Process date") @PathVariable("date") String date) {
        LOGGER.debug("Replacing igms for merge process {} : {}", processName, date);
        String decodedDate = URLDecoder.decode(date, StandardCharsets.UTF_8);
        ZonedDateTime dateTime = ZonedDateTime.parse(decodedDate);

        Map<String, IgmReplacingInfo> res = mergeOrchestratorService.replaceIGMs(processName, dateTime);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res);

    }
}

