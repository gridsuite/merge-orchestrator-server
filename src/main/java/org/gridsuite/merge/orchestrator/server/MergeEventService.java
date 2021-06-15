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
import java.util.function.Supplier;
import java.util.logging.Level;

import org.gridsuite.merge.orchestrator.server.dto.IgmStatus;
import org.gridsuite.merge.orchestrator.server.dto.MergeStatus;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class MergeEventService {

    private static final String CATEGORY_BROKER_OUTPUT = MergeEventService.class.getName()
            + ".output-broker-messages";

    private MergeRepository mergeRepository;

    private IgmRepository igmRepository;

    private final EmitterProcessor<Message<String>> mergeInfosPublisher = EmitterProcessor.create();

    @Bean
    public Supplier<Flux<Message<String>>> publishMerge() {
        return () -> mergeInfosPublisher.log(CATEGORY_BROKER_OUTPUT, Level.FINE);
    }

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
        mergeInfosPublisher.onNext(MessageBuilder
                .withPayload("")
                .setHeader("processUuid", processUuid)
                .setHeader("businessProcess", businessProcess)
                .setHeader("date", date.format(DateTimeFormatter.ISO_DATE_TIME))
                .setHeader("tso", tso)
                .setHeader("status", status.name())
                .build());
    }

    public void addMergeEvent(UUID processUuid, String businessProcess, ZonedDateTime date, MergeStatus status) {
        // Use of UTC Zone to store in database
        var mergeEntity = getOrCreateMergeEntity(processUuid, date);
        mergeEntity.setStatus(status.name());
        mergeRepository.save(mergeEntity);
        mergeInfosPublisher.onNext(MessageBuilder
                .withPayload("")
                .setHeader("processUuid", processUuid)
                .setHeader("businessProcess", businessProcess)
                .setHeader("date", date.format(DateTimeFormatter.ISO_DATE_TIME))
                .setHeader("status", status.name())
                .build());
    }

    MergeEntity getOrCreateMergeEntity(UUID processUuid, ZonedDateTime date) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        var key = new MergeEntityKey(processUuid, localDateTime);
        return mergeRepository.findById(key).orElseGet(() -> new MergeEntity(key, null));
    }
}
