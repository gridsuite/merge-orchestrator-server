/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Repository
public interface IgmRepository extends JpaRepository<IgmEntity, IgmEntityKey> {

    List<IgmEntity> findByKeyProcessUuid(UUID processUuid);

    List<IgmEntity> findByKeyProcessUuidAndKeyDate(UUID processUuid, LocalDateTime date);

    @Transactional
    void deleteByKeyProcessUuid(UUID processUuid);

    Optional<IgmEntity> findByKeyProcessUuidAndKeyDateAndKeyTso(UUID processUuid, LocalDateTime date, String tso);
}
