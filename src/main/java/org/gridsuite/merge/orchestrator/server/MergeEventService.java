/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.gridsuite.merge.orchestrator.server.repositories.MergeEntity;
import org.gridsuite.merge.orchestrator.server.repositories.MergeEntityKey;
import org.gridsuite.merge.orchestrator.server.repositories.MergeRepository;
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

    private final EmitterProcessor<Message<String>> mergeInfosPublisher = EmitterProcessor.create();

    @Bean
    public Supplier<Flux<Message<String>>> publishMerge() {
        return () -> mergeInfosPublisher.log(CATEGORY_BROKER_OUTPUT, Level.FINE);
    }

    public MergeEventService(MergeRepository mergeRepository) {
        this.mergeRepository = mergeRepository;
    }

    public void addMergeEvent(String payload, String tso, String type, ZonedDateTime date,
                              UUID networkUuid, String process) {
        mergeRepository.save(new MergeEntity(new MergeEntityKey(process, date.toLocalDateTime()), type, networkUuid));
        mergeInfosPublisher.onNext(MessageBuilder
                .withPayload(payload)
                .setHeader("tso", tso)
                .setHeader("type", type)
                .setHeader("date", date.format(DateTimeFormatter.ISO_DATE_TIME))
                .setHeader("process", process).build());
    }
}
