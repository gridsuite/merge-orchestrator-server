/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.network.store.client.NetworkStoreService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(MockitoJUnitRunner.class)
public class CopyToNetworkStoreServiceTest {

    @Mock
    private NetworkStoreService networkStoreService;

    private CopyToNetworkStoreService copyToNetworkStoreService;

    private UUID randomUuid = UUID.randomUUID();

    @Before
    public void setUp() {
        copyToNetworkStoreService = new CopyToNetworkStoreService(networkStoreService);
    }

    @Test
    public void test() {
        NetworkFactory networkFactory = NetworkFactory.find("Default");
        given(networkStoreService.getNetworkFactory()).willReturn(networkFactory);
        given(networkStoreService.getNetworkUuid(any())).willReturn(randomUuid);
        Network network = networkFactory.createNetwork("fr", "iidm");
        UUID copy = copyToNetworkStoreService.copy(network);
        Assert.assertEquals(randomUuid, copy);
    }
}
