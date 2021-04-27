/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.repositories;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Repository
public interface IgmRepository extends CassandraRepository<IgmEntity, IgmEntityKey> {

    @Query("SELECT * FROM merge_igm WHERE processUuid = :processUuid")
    List<IgmEntity> findByProcessUuid(UUID processUuid);

    @Query("SELECT * FROM merge_igm WHERE processUuid = :processUuid AND date = :date")
    List<IgmEntity> findByProcessUuidAndDate(UUID processUuid, LocalDateTime date);

    @Query("DELETE FROM merge_igm WHERE processUuid = :processUuid")
    void deleteByProcessUuid(UUID processUuid);

    @Query("SELECT * FROM merge_igm WHERE processUuid = :processUuid AND date >= :minDate AND date <= :maxDate")
    List<IgmEntity> findByProcessUuidAndInterval(UUID processUuid, LocalDateTime minDate, LocalDateTime maxDate);

    @Query("SELECT * FROM merge_igm WHERE processUuid = :processUuid AND date = :date AND tso = :tso")
    Optional<IgmEntity> findByProcessUuidAndDateAndTso(UUID processUuid, LocalDateTime date, String tso);

    @Query("UPDATE merge_igm SET status = :status, networkUuid = :networkUuid, replacingDate = :replacingDate, replacingBusinessProcess = :replacingBusinessProcess, boundaries = :boundaries WHERE processUuid = :processUuid AND date = :date AND tso = :tso")
    void updateReplacingIgm(UUID processUuid, LocalDateTime date, String tso,
                            String status, UUID networkUuid, LocalDateTime replacingDate, String replacingBusinessProcess,
                            List<UUID> boundaries);
}
