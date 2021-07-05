/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.gridsuite.merge.orchestrator.server.dto.IgmStatus;
import org.gridsuite.merge.orchestrator.server.dto.MergeStatus;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class MergeEventService {

    private MergeRepository mergeRepository;

    private IgmRepository igmRepository;

    private static final String CATEGORY_BROKER_OUTPUT = MergeEventService.class.getName() + ".output-broker-messages";

    private static final String PROCESS_UUID = "processUuid";
    private static final String BUSINESS_PROCESS = "businessProcess";

    private static final Logger LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    @Autowired
    private StreamBridge mergeInfosPublisher;

    public MergeEventService(MergeRepository mergeRepository, IgmRepository igmRepository) {
        this.mergeRepository = mergeRepository;
        this.igmRepository = igmRepository;
    }

    public void addMergeIgmEvent(UUID processUuid, String businessProcess, ZonedDateTime date, String tso, IgmStatus status, UUID networkUuid, UUID caseUuid,
                                 ZonedDateTime replacingDate, String replacingBusinessProcess, String eqBoundary, String tpBoundary) {
        // Use of UTC Zone to store in database
        LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        LocalDateTime localReplacingDateTime = replacingDate != null ? LocalDateTime.ofInstant(replacingDate.toInstant(), ZoneOffset.UTC) : null;

        var mergeEntity = getOrCreateMergeEntity(processUuid, date);
        mergeEntity.setStatus(null);
        mergeRepository.save(mergeEntity);
        igmRepository.save(new IgmEntity(new IgmEntityKey(processUuid, localDateTime, tso), status.name(), networkUuid, caseUuid,
                localReplacingDateTime, replacingBusinessProcess, eqBoundary, tpBoundary));

        Message<String> message = MessageBuilder
                .withPayload("")
                .setHeader(PROCESS_UUID, processUuid)
                .setHeader(BUSINESS_PROCESS, businessProcess)
                .setHeader("date", date.format(DateTimeFormatter.ISO_DATE_TIME))
                .setHeader("tso", tso)
                .setHeader("status", status.name())
                .build();
        sendMergeMessage(message);
    }

    public void addMergeEvent(UUID processUuid, String businessProcess, ZonedDateTime date, MergeStatus status) {
        // Use of UTC Zone to store in database
        var mergeEntity = getOrCreateMergeEntity(processUuid, date);
        mergeEntity.setStatus(status.name());
        mergeRepository.save(mergeEntity);
        Message<String> message = MessageBuilder
                .withPayload("")
                .setHeader(PROCESS_UUID, processUuid)
                .setHeader(BUSINESS_PROCESS, businessProcess)
                .setHeader("date", date.format(DateTimeFormatter.ISO_DATE_TIME))
                .setHeader("status", status.name())
                .build();
        sendMergeMessage(message);
    }

    private void sendMergeMessage(Message<String> message) {
        LOGGER.debug("Sending message : {}", message);
        mergeInfosPublisher.send("publishMerge-out-0", message);
    }

    public void addErrorEvent(UUID processUuid, String businessProcess, String errorMessage) {
        Message<String> message = MessageBuilder
            .withPayload("")
            .setHeader(PROCESS_UUID, processUuid)
            .setHeader(BUSINESS_PROCESS, businessProcess)
            .setHeader("error", errorMessage)
            .build();
        sendMergeMessage(message);
    }
  
    MergeEntity getOrCreateMergeEntity(UUID processUuid, ZonedDateTime date) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        return mergeRepository.findByKeyProcessUuidAndKeyDate(processUuid, localDateTime)
            .orElseGet(() -> new MergeEntity(new MergeEntityKey(processUuid, localDateTime), null));
    }
}
