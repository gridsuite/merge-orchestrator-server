/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import java.util.UUID;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.xml.NetworkXml;
import com.powsybl.network.store.client.NetworkStoreService;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
@ComponentScan(basePackageClasses = { NetworkStoreService.class })
public class CopyToNetworkStoreService {

    private NetworkStoreService networkStoreService;

    public CopyToNetworkStoreService(NetworkStoreService networkStoreService) {
        this.networkStoreService = networkStoreService;
    }

    public UUID copy(Network network) {
        Network copy = NetworkXml.copy(network, networkStoreService.getNetworkFactory());
        networkStoreService.flush(copy);
        return networkStoreService.getNetworkUuid(copy);
    }

}
