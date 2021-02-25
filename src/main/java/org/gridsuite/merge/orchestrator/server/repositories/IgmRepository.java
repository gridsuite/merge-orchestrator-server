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

    @Query("SELECT * FROM merge_igm WHERE process = :process")
    List<IgmEntity> findByProcess(String process);

    @Query("SELECT * FROM merge_igm WHERE process = :process AND date = :date")
    List<IgmEntity> findByProcessAndDate(String process, LocalDateTime date);

    @Query("DELETE FROM merge_igm WHERE process = :process")
    void deleteByProcess(String process);

    @Query("SELECT * FROM merge_igm WHERE process = :process AND date >= :minDate AND date <= :maxDate")
    List<IgmEntity> findByProcessAndInterval(String process, LocalDateTime minDate, LocalDateTime maxDate);

    @Query("SELECT * FROM merge_igm WHERE process = :process AND date = :date AND tso = :tso")
    Optional<IgmEntity> findByProcessAndDateAndTso(String process, LocalDateTime date, String tso);

    @Query("UPDATE merge_igm SET status = :status, networkUuid = :networkUuid, replacingDate = :replacingDate, replacingBusinessProcess = :replacingBusinessProcess WHERE process = :process AND date = :date AND tso = :tso")
    void updateReplacingIgm(String process, LocalDateTime date, String tso,
                            String status, UUID networkUuid, LocalDateTime replacingDate, String replacingBusinessProcess);
}
