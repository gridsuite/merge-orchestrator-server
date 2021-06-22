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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Repository
public interface MergeRepository extends JpaRepository<MergeEntity, MergeEntityKey> {

    @Transactional
    void deleteByKeyProcessUuid(UUID processUuid);

    interface MergeIgm {
        UUID getProcessUuid();

        LocalDateTime getDate();

        String getStatus();

        String getTso();

        String getIgmStatus();

        LocalDateTime getReplacingDate();

        String getReplacingBusinessProcess();
    }

    @Query(value = "SELECT m.key.processUuid AS processUuid, m.key.date AS date, m.status AS status, igm.key.tso AS tso, igm.status AS igmStatus, igm.replacingDate AS replacingDate, igm.replacingBusinessProcess AS replacingBusinessProcess from MergeEntity m JOIN IgmEntity igm ON m.key.processUuid = igm.key.processUuid AND m.key.date = igm.key.date WHERE m.key.processUuid = :processUuid")
    List<MergeIgm> findMergeWithIgmsByProcessUuid(UUID processUuid);

    @Query(value = "SELECT m.key.processUuid AS processUuid, m.key.date AS date, m.status AS status, igm.key.tso AS tso, igm.status AS igmStatus, igm.replacingDate AS replacingDate, igm.replacingBusinessProcess AS replacingBusinessProcess from MergeEntity m JOIN IgmEntity igm ON m.key.processUuid = igm.key.processUuid AND m.key.date = igm.key.date WHERE m.key.processUuid = :processUuid and m.key.date >= :minDate and m.key.date <= :maxDate")
    List<MergeIgm> findMergeWithIgmsByProcessUuidAndInterval(UUID processUuid, LocalDateTime minDate, LocalDateTime maxDate);
}
