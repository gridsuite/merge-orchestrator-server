/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * @author Seddik Yengui <seddik.yengui at rte-france.com
 */

// Today we don't send notification inside @Transactional block. If this behavior change, we should use @PostCompletion to
// make sure that the notification is sent only when all the work inside @Transactional block is done.
@Service
public class NotificationService {

    private static final String CATEGORY_BROKER_OUTPUT = MergeEventService.class.getName() + ".output-broker-messages";

    private static final String PROCESS_UUID_HEADER = "processUuid";
    private static final String BUSINESS_PROCESS_HEADER = "businessProcess";
    private static final String DATE_HEADER = "date";
    private static final String STATUS_HEADER = "status";
    private static final String TSO_HEADER = "tso";
    private static final String ERROR_HEADER = "error";

    private static final Logger LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    @Autowired
    private StreamBridge mergeInfosPublisher;

    private void sendMergeMessage(Message<String> message) {
        LOGGER.debug("Sending message : {}", message);
        mergeInfosPublisher.send("publishMerge-out-0", message);
    }

    public void emitMergeIgmEvent(UUID processUuid, String businessProcess, String date, String tso, String status) {
        Message<String> message = MessageBuilder
                .withPayload("")
                .setHeader(PROCESS_UUID_HEADER, processUuid)
                .setHeader(BUSINESS_PROCESS_HEADER, businessProcess)
                .setHeader(DATE_HEADER, date)
                .setHeader(TSO_HEADER, tso)
                .setHeader(STATUS_HEADER, status)
                .build();
        sendMergeMessage(message);
    }

    public void emitMergeEvent(UUID processUuid, String businessProcess, String date, String status) {
        Message<String> message = MessageBuilder
                .withPayload("")
                .setHeader(PROCESS_UUID_HEADER, processUuid)
                .setHeader(BUSINESS_PROCESS_HEADER, businessProcess)
                .setHeader(DATE_HEADER, date)
                .setHeader(STATUS_HEADER, status)
                .build();
        sendMergeMessage(message);
    }

    public void emitErrorEvent(UUID processUuid, String businessProcess, String errorMessage) {
        Message<String> message = MessageBuilder
                .withPayload("")
                .setHeader(PROCESS_UUID_HEADER, processUuid)
                .setHeader(BUSINESS_PROCESS_HEADER, businessProcess)
                .setHeader(ERROR_HEADER, errorMessage)
                .build();
        sendMergeMessage(message);
    }
}
