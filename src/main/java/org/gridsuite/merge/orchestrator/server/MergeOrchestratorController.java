/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.commons.report.ReportNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + MergeOrchestratorApi.API_VERSION)
@Transactional
@Tag(name = "Merge orchestrator server")
@ComponentScan(basePackageClasses = MergeOrchestratorService.class)
public class MergeOrchestratorController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeOrchestratorController.class);

    private final MergeOrchestratorService mergeOrchestratorService;

    public MergeOrchestratorController(MergeOrchestratorService mergeOrchestratorService) {
        this.mergeOrchestratorService = mergeOrchestratorService;
    }

    @GetMapping(value = "{processUuid}/merges", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get merges for a process")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of all merges for a process")})
    public ResponseEntity<List<Merge>> getMerges(@PathVariable("processUuid") UUID processUuid,
                                                 @RequestParam(value = "minDate", required = false) String minDate,
                                                 @RequestParam(value = "maxDate", required = false) String maxDate) {
        List<Merge> merges;
        if (minDate != null && maxDate != null) {
            String decodedMinDate = URLDecoder.decode(minDate, StandardCharsets.UTF_8);
            ZonedDateTime minDateTime = ZonedDateTime.parse(decodedMinDate);
            String decodedMaxDate = URLDecoder.decode(maxDate, StandardCharsets.UTF_8);
            ZonedDateTime maxDateTime = ZonedDateTime.parse(decodedMaxDate);
            merges = mergeOrchestratorService.getMerges(processUuid, minDateTime, maxDateTime);
        } else {
            merges = mergeOrchestratorService.getMerges(processUuid);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(merges);
    }

    @GetMapping(value = "{processUuid}/{date}/export/{format}")
    @Operation(summary = "Export a merge from the network-store")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The export merge for process")})
    public ResponseEntity<byte[]> exportNetwork(@Parameter(description = "Process uuid") @PathVariable("processUuid") UUID processUuid,
                                                @Parameter(description = "Process date") @PathVariable("date") String date,
                                                @Parameter(description = "Export format") @PathVariable("format") String format) {
        LOGGER.debug("Exporting merge for process {} : {}", processUuid, date);
        String decodedDate = URLDecoder.decode(date, StandardCharsets.UTF_8);
        ZonedDateTime dateTime = ZonedDateTime.parse(decodedDate);

        FileInfos exportedMergeInfo = mergeOrchestratorService.exportMerge(processUuid, dateTime, format);

        HttpHeaders header = new HttpHeaders();
        header.setContentDisposition(ContentDisposition.builder("attachment").filename(exportedMergeInfo.getName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(header).contentType(MediaType.APPLICATION_OCTET_STREAM).body(exportedMergeInfo.getData());
    }

    @PutMapping(value = "{processUuid}/{date}/replace-igms")
    @Operation(summary = "Replace missing or invalid igms")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "IGMs replaced")})
    public ResponseEntity<Map<String, IgmReplacingInfo>> replaceIGMs(@Parameter(description = "Process uuid") @PathVariable("processUuid") UUID processUuid,
                                                                     @Parameter(description = "Process date") @PathVariable("date") String date) {
        LOGGER.debug("Replacing igms for merge process {} : {}", processUuid, date);
        String decodedDate = URLDecoder.decode(date, StandardCharsets.UTF_8);
        ZonedDateTime dateTime = ZonedDateTime.parse(decodedDate);

        Map<String, IgmReplacingInfo> res = mergeOrchestratorService.replaceIGMs(processUuid, dateTime);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res);
    }

    @GetMapping(value = "{processUuid}/{date}/report", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get merge report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report for process"), @ApiResponse(responseCode = "404", description = "The process not found")})
    public ResponseEntity<ReportNode> getReport(@Parameter(description = "Process uuid") @PathVariable("processUuid") UUID processUuid,
                                                @Parameter(description = "Process date") @PathVariable("date") String date) {
        LOGGER.debug("Get report for merge process {} : {}", processUuid, date);
        String decodedDate = URLDecoder.decode(date, StandardCharsets.UTF_8);
        LocalDateTime dateTime = LocalDateTime.ofInstant(ZonedDateTime.parse(decodedDate).toInstant(), ZoneOffset.UTC);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mergeOrchestratorService.getReport(processUuid, dateTime));
    }

    @DeleteMapping(value = "{processUuid}/{date}/report")
    @Operation(summary = "Delete merge report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report for process deleted"), @ApiResponse(responseCode = "404", description = "The process not found")})
    public ResponseEntity<Void> deleteReport(@Parameter(description = "Process uuid") @PathVariable("processUuid") UUID processUuid,
                                             @Parameter(description = "Process date") @PathVariable("date") String date) {
        LOGGER.debug("Delete report for merge process {} : {}", processUuid, date);

        String decodedDate = URLDecoder.decode(date, StandardCharsets.UTF_8);
        LocalDateTime dateTime = LocalDateTime.ofInstant(ZonedDateTime.parse(decodedDate).toInstant(), ZoneOffset.UTC);
        mergeOrchestratorService.deleteReport(processUuid, dateTime);

        return ResponseEntity.ok().build();
    }
}

