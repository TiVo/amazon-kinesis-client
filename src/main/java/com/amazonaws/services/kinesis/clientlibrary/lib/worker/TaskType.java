/*
 * Copyright 2012-2013 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Amazon Software License (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://aws.amazon.com/asl/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.services.kinesis.clientlibrary.lib.worker;

/**
 * Enumerates types of tasks executed as part of processing a shard.
 */
enum TaskType {
    /**
     * Polls and waits until parent shard(s) have been fully processed.
     */
    BLOCK_ON_PARENT_SHARDS,
    /**
     * Delays until the timeout period on stolen shards expires
     */
    BLOCK_ON_STOLEN_SHARD,
    /**
     * Initialization of RecordProcessor (and Amazon Kinesis Client Library internal state for a shard).
     */
    INITIALIZE,
    /**
     * Fetching and processing of records.
     */
    PROCESS,
    /**
     * Shutdown of RecordProcessor.
     */
    SHUTDOWN,
    /**
     * Sync leases/activities corresponding to Kinesis shards.
     */
    SHARDSYNC,
    /**
     * Catch up a shard from external storage
     */
    CATCHUP,
}
