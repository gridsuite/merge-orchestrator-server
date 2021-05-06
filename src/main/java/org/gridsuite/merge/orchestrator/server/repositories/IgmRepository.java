/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Repository
public interface IgmRepository extends JpaRepository<IgmEntity, IgmEntityKey> {

    List<IgmEntity> findByProcessUuid(UUID processUuid);

    List<IgmEntity> findByProcessUuidAndDate(UUID processUuid, LocalDateTime date);

    void deleteByProcessUuid(UUID processUuid);

    @Query(value = "SELECT igm from #{#entityName} as igm where igm.processUuid = :processUuid and igm.date >= :minDate and igm.date <= :maxDate")
    List<IgmEntity> findByProcessUuidAndInterval(UUID processUuid, LocalDateTime minDate, LocalDateTime maxDate);

    Optional<IgmEntity> findByProcessUuidAndDateAndTso(UUID processUuid, LocalDateTime date, String tso);
}
