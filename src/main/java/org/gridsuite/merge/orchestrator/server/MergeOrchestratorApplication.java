/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.fasterxml.jackson.databind.Module;
import com.powsybl.commons.reporter.ReporterModelJsonModule;
import com.powsybl.loadflow.json.LoadFlowResultJsonModule;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.ws.commons.Utils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@SpringBootApplication(scanBasePackageClasses = { MergeOrchestratorApplication.class, NetworkStoreService.class })
public class MergeOrchestratorApplication {
    public static void main(String[] args) {
        Utils.initProperties();
        SpringApplication.run(MergeOrchestratorApplication.class, args);
    }

    @Bean
    public Module createLoadFlowResultModule() {
        return new LoadFlowResultJsonModule();
    }

    @Bean
    public Module createReporterModelModule() {
        ReporterModelJsonModule reporterModelJsonModule = new ReporterModelJsonModule();
        reporterModelJsonModule.setSerializers(null); // FIXME: remove when dicos will be used on the front side
        return reporterModelJsonModule;
    }
}
