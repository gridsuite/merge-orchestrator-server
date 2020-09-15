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
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Repository
public interface MergeIgmRepository extends CassandraRepository<MergeIgmEntity, UUID> {

    @Query("SELECT * FROM merge_igm WHERE process = :process")
    List<MergeIgmEntity> findByProcess(String process);

    @Query("SELECT * FROM merge_igm WHERE process = :process AND date = :date")
    List<MergeIgmEntity> findByProcessAndDate(String process, LocalDateTime date);
}
