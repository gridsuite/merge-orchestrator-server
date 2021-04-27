/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.repositories;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Repository
public interface MergeRepository extends CassandraRepository<MergeEntity, MergeEntityKey> {

    @Query("SELECT * FROM merge WHERE processUuid = :processUuid")
    List<MergeEntity> findByProcessUuid(@Param("processUuid") UUID processUuid);

    @Query("DELETE FROM merge WHERE processUuid = :processUuid")
    void deleteByProcessUuid(@Param("processUuid") UUID processUuid);

    @Query("SELECT * FROM merge WHERE processUuid = :processUuid AND date >= :minDate AND date <= :maxDate")
    List<MergeEntity> findByProcessUuidAndInterval(UUID processUuid, LocalDateTime minDate, LocalDateTime maxDate);
}
