/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.repositories.MergeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.config.CassandraClusterFactoryBean;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 */
@Configuration
@PropertySource(value = {"classpath:cassandra.properties"})
@PropertySource(value = {"file:/config/cassandra.properties"}, ignoreResourceNotFound = true)
@EnableCassandraRepositories(basePackageClasses = MergeRepository.class)
public class CassandraConfig extends AbstractCassandraConfiguration {

    static final String KEYSPACE_MERGE_ORCHESTRATOR = "merge_orchestrator";

    @Override
    protected String getKeyspaceName() {
        return KEYSPACE_MERGE_ORCHESTRATOR;
    }

    @Bean
    public CassandraClusterFactoryBean cluster(Environment env) {
        CassandraClusterFactoryBean clusterFactory = new CassandraClusterFactoryBean();
        clusterFactory.setContactPoints(env.getRequiredProperty("cassandra.contact-points"));
        clusterFactory.setPort(Integer.parseInt(env.getRequiredProperty("cassandra.port")));
        return clusterFactory;
    }
}
