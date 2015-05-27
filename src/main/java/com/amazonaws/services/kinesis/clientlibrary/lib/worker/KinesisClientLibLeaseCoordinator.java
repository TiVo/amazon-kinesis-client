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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.services.kinesis.clientlibrary.exceptions.KinesisClientLibDependencyException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.KinesisClientLibException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ShutdownException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ThrottlingException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.internal.KinesisClientLibIOException;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.ICheckpoint;
import com.amazonaws.services.kinesis.leases.exceptions.DependencyException;
import com.amazonaws.services.kinesis.leases.exceptions.InvalidStateException;
import com.amazonaws.services.kinesis.leases.exceptions.ProvisionedThroughputException;
import com.amazonaws.services.kinesis.leases.impl.LeaseCoordinator;
import com.amazonaws.services.kinesis.leases.impl.KinesisClientLease;
import com.amazonaws.services.kinesis.leases.interfaces.ILeaseManager;
import com.amazonaws.services.kinesis.metrics.interfaces.IMetricsFactory;

/**
 * This class is used to coordinate/manage leases owned by this worker process and to get/set checkpoints.
 */
class KinesisClientLibLeaseCoordinator extends LeaseCoordinator<KinesisClientLease> implements ICheckpoint {

    private static final Log LOG = LogFactory.getLog(KinesisClientLibLeaseCoordinator.class);
    private final ILeaseManager<KinesisClientLease> leaseManager;
    private final long initialLeaseTableReadCapacity = 10L;
    private final long initialLeaseTableWriteCapacity = 10L;

    /**
     * @param leaseManager Lease manager which provides CRUD lease operations.
     * @param workerIdentifier Used to identify this worker process
     * @param leaseDurationMillis Duration of a lease in milliseconds
     * @param epsilonMillis Delta for timing operations (e.g. checking lease expiry)
     */
    public KinesisClientLibLeaseCoordinator(ILeaseManager<KinesisClientLease> leaseManager,
            String workerIdentifier,
            long leaseDurationMillis,
            long epsilonMillis) {
        super(leaseManager, workerIdentifier, leaseDurationMillis, epsilonMillis);
        this.leaseManager = leaseManager;
    }

    /**
     * @param leaseManager Lease manager which provides CRUD lease operations.
     * @param workerIdentifier Used to identify this worker process
     * @param leaseDurationMillis Duration of a lease in milliseconds
     * @param epsilonMillis Delta for timing operations (e.g. checking lease expiry)
     * @param metricsFactory Metrics factory used to emit metrics
     */
    public KinesisClientLibLeaseCoordinator(ILeaseManager<KinesisClientLease> leaseManager,
            String workerIdentifier,
            long leaseDurationMillis,
            long epsilonMillis,
            IMetricsFactory metricsFactory) {
        super(leaseManager, workerIdentifier, leaseDurationMillis, epsilonMillis, metricsFactory);
        this.leaseManager = leaseManager;
    }

    /**
     * Sets the checkpoint for a shard and updates ownerSwitchesSinceCheckpoint.
     * 
     * @param shardId shardId to update the checkpoint for
     * @param checkpoint checkpoint value to set
     * @param concurrencyToken obtained by calling Lease.getConcurrencyToken for a currently held lease
     * 
     * @return true if checkpoint update succeeded, false otherwise
     * 
     * @throws InvalidStateException if lease table does not exist
     * @throws ProvisionedThroughputException if DynamoDB update fails due to lack of capacity
     * @throws DependencyException if DynamoDB update fails in an unexpected way
     */
    boolean setCheckpoint(String shardId, String checkpoint, UUID concurrencyToken)
        throws DependencyException, InvalidStateException, ProvisionedThroughputException {
        KinesisClientLease lease = getCurrentlyHeldLease(shardId);
        if (lease == null) {
            LOG.info(String.format(
                    "Worker %s could not update checkpoint for shard %s because it does not hold the lease",
                    getWorkerIdentifier(),
                    shardId));
            return false;
        }

        lease.setCheckpoint(checkpoint);
        lease.setOwnerSwitchesSinceCheckpoint(0L);

        return updateLease(lease, concurrencyToken);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCheckpoint(String shardId, String checkpointValue, String concurrencyToken)
        throws KinesisClientLibException {
        try {
            boolean wasSuccessful = setCheckpoint(shardId, checkpointValue, UUID.fromString(concurrencyToken));
            if (!wasSuccessful) {
                throw new ShutdownException("Can't update checkpoint - instance doesn't hold the lease for this shard");
            }
        } catch (ProvisionedThroughputException e) {
            throw new ThrottlingException("Got throttled while updating checkpoint.", e);
        } catch (InvalidStateException e) {
            String message = "Unable to save checkpoint for shardId " + shardId;
            LOG.error(message, e);
            throw new com.amazonaws.services.kinesis.clientlibrary.exceptions.InvalidStateException(message, e);
        } catch (DependencyException e) {
            throw new KinesisClientLibDependencyException("Unable to save checkpoint for shardId " + shardId, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCheckpoint(String shardId) throws KinesisClientLibException {
        try {
            return leaseManager.getLease(shardId).getCheckpoint();
        } catch (DependencyException e) {
            String message = "Unable to fetch checkpoint for shardId " + shardId;
            LOG.error(message, e);
            throw new KinesisClientLibIOException(message, e);
        } catch (InvalidStateException e) {
            String message = "Unable to fetch checkpoint for shardId " + shardId;
            LOG.error(message, e);
            throw new KinesisClientLibIOException(message, e);
        } catch (ProvisionedThroughputException e) {
            String message = "Unable to fetch checkpoint for shardId " + shardId;
            LOG.error(message, e);
            throw new KinesisClientLibIOException(message, e);
        }
    }

    /**
     * @return Current shard/lease assignments
     */
    public List<ShardInfo> getCurrentAssignments() {
        List<ShardInfo> assignments = new LinkedList<ShardInfo>();
        Collection<KinesisClientLease> leases = getAssignments();
        if ((leases != null) && (!leases.isEmpty())) {
            for (KinesisClientLease lease : leases) {
                Set<String> parentShardIds = lease.getParentShardIds();
                ShardInfo assignment =
                        new ShardInfo(
                                lease.getLeaseKey(), 
                                lease.getConcurrencyToken().toString(), 
                                parentShardIds);
                assignments.add(assignment);
            }
        }
        return assignments;
    }

    /**
     * Initialize the lease coordinator (create the lease table if needed).
     * @throws DependencyException
     * @throws ProvisionedThroughputException
     */
    void initialize() throws ProvisionedThroughputException, DependencyException, IllegalStateException {
        final boolean newTableCreated =
                leaseManager.createLeaseTableIfNotExists(initialLeaseTableReadCapacity, initialLeaseTableWriteCapacity);
        if (newTableCreated) {
            LOG.info("Created new lease table for coordinator");
        }
        // Need to wait for table in active state.
        final long secondsBetweenPolls = 10L;
        final long timeoutSeconds = 600L;
        final boolean isTableActive = leaseManager.waitUntilLeaseTableExists(secondsBetweenPolls, timeoutSeconds);
        if (!isTableActive) {
            throw new DependencyException(new IllegalStateException("Creating table timeout"));
        }
    }

    /**
     * Package access for testing.
     * 
     * @throws DependencyException
     * @throws InvalidStateException
     */
    void runLeaseTaker() throws DependencyException, InvalidStateException {
        super.runTaker();
    }

    /**
     * Package access for testing.
     * 
     * @throws DependencyException
     * @throws InvalidStateException
     */
    void runLeaseRenewer() throws DependencyException, InvalidStateException {
        super.runRenewer();
    }

    /**
     * Used to get information about leases for Kinesis shards (e.g. sync shards and leases, check on parent shard
     * completion).
     * 
     * @return LeaseManager
     */
    ILeaseManager<KinesisClientLease> getLeaseManager() {
        return leaseManager;
    }

}
