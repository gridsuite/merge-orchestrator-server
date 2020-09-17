/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.repositories.ProcessConfigEntity;
import org.gridsuite.merge.orchestrator.server.repositories.ProcessConfigRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@Service
public class MergeOrchestratorConfigService {

    private final ProcessConfigRepository processConfigRepository;

    public MergeOrchestratorConfigService(ProcessConfigRepository processConfigRepository) {
        this.processConfigRepository = processConfigRepository;
    }

    public List<String> getTsos() {
        return getConfigs().stream().flatMap(config -> config.getTsos().stream()).collect(Collectors.toList());
    }

    List<ProcessConfigEntity> getConfigs() {
        return processConfigRepository.findAll();
    }

    Optional<ProcessConfigEntity> getConfig(String process) {
        return processConfigRepository.findById(process);
    }

    void addConfig(ProcessConfigEntity processConfigEntity) {
        processConfigRepository.save(processConfigEntity);
    }

    public void deleteConfig(String process) {
        processConfigRepository.deleteById(process);
    }
}
