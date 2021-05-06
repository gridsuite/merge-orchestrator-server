[![Actions Status](https://github.com/gridsuite/merge-orchestrator-server/workflows/CI/badge.svg)](https://github.com/gridsuite/merge-orchestrator-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Amerge-orchestrator-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Amerge-orchestrator-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
# merge-orchestrator-server

To automatically generate the sql schema file you can use the following command:

mvn package -DskipTests && rm src/main/resources/merge_orchestrator.sql && java -jar target/gridsuite-merge-orchestrator-server-1.0.0-SNAPSHOT-exec.jar --spring.jpa.properties.javax.persistence.schema-generation.scripts.action=create 
