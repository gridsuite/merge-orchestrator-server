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

    public void addMergeIgmEvent(String process, String businessProcess, ZonedDateTime date, String tso, IgmStatus status, UUID networkUuid,
                                 ZonedDateTime replacingDate, String replacingBusinessProcess) {
        // Use of UTC Zone to store in cassandra database
        LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        LocalDateTime localReplacingDateTime = replacingDate != null ? LocalDateTime.ofInstant(replacingDate.toInstant(), ZoneOffset.UTC) : null;

        mergeRepository.save(new MergeEntity(new MergeEntityKey(process, localDateTime), null));
        igmRepository.save(new IgmEntity(new IgmEntityKey(process, localDateTime, tso), status.name(), networkUuid,
                localReplacingDateTime, replacingBusinessProcess));
        mergeInfosPublisher.onNext(MessageBuilder
                .withPayload("")
                .setHeader("process", process)
                .setHeader("businessProcess", businessProcess)
                .setHeader("date", date.format(DateTimeFormatter.ISO_DATE_TIME))
                .setHeader("tso", tso)
                .setHeader("status", status.name())
                .build());
    }

    public void addMergeEvent(String process, String businessProcess, ZonedDateTime date, MergeStatus status) {
        // Use of UTC Zone to store in cassandra database
        LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        mergeRepository.save(new MergeEntity(new MergeEntityKey(process, localDateTime), status.name()));
        mergeInfosPublisher.onNext(MessageBuilder
                .withPayload("")
                .setHeader("process", process)
                .setHeader("businessProcess", businessProcess)
                .setHeader("date", date.format(DateTimeFormatter.ISO_DATE_TIME))
                .setHeader("status", status.name())
                .build());
    }
}
