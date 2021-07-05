/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.springframework.http.HttpStatus;

import java.util.Objects;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MergeOrchestratorException extends RuntimeException {

    public enum Type {
        MERGE_CONFIG_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
        MERGE_NOT_FOUND(HttpStatus.NOT_FOUND),
        MERGE_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND),
        MERGE_REPORT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
        MERGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

        public final HttpStatus status;
        private final String message;

        HttpStatus getStatus() {
            return status;
        }

        Type(HttpStatus status) {
            this(status, null);
        }

        Type(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private final Type type;

    public MergeOrchestratorException(Type type) {
        super(Objects.requireNonNull(type.name()) + ((type.message == null) ? "" : " : " + type.message));
        this.type = type;
    }

    public MergeOrchestratorException(Type type, Exception cause) {
        super(Objects.requireNonNull(type.name()) + " : " + ((cause.getMessage() == null) ? cause.getClass().getName() : cause.getMessage()), cause);
        this.type = type;
    }

    public MergeOrchestratorException(Type type, String message) {
        super(Objects.requireNonNull(type.name()) + " : " + Objects.requireNonNull(message));
        this.type = type;
    }

    Type getType() {
        return type;
    }
}
