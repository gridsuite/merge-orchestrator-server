/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.IgmStatus;
import org.gridsuite.merge.orchestrator.server.dto.MergeStatus;
import org.gridsuite.merge.orchestrator.server.repositories.IgmEntity;
import org.gridsuite.merge.orchestrator.server.repositories.IgmEntityKey;
import org.gridsuite.merge.orchestrator.server.repositories.IgmRepository;
import org.gridsuite.merge.orchestrator.server.repositories.MergeEntity;
import org.gridsuite.merge.orchestrator.server.repositories.MergeEntityKey;
import org.gridsuite.merge.orchestrator.server.repositories.MergeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class MergeEventService {

    private MergeRepository mergeRepository;

    private IgmRepository igmRepository;

    @Autowired
    private NotificationService notificationService;

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

        notificationService.emitMergeIgmEvent(processUuid,
                businessProcess,
                date.format(DateTimeFormatter.ISO_DATE_TIME),
                tso,
                status.name());
    }

    public void addMergeEvent(UUID processUuid, String businessProcess, ZonedDateTime date, MergeStatus status) {
        // Use of UTC Zone to store in database
        var mergeEntity = getOrCreateMergeEntity(processUuid, date);
        mergeEntity.setStatus(status.name());
        mergeRepository.save(mergeEntity);
        notificationService.emitMergeEvent(processUuid,
                businessProcess,
                date.format(DateTimeFormatter.ISO_DATE_TIME),
                status.name());
    }

    MergeEntity getOrCreateMergeEntity(UUID processUuid, ZonedDateTime date) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        return mergeRepository.findByKeyProcessUuidAndKeyDate(processUuid, localDateTime)
            .orElseGet(() -> new MergeEntity(new MergeEntityKey(processUuid, localDateTime), null));
    }
}
