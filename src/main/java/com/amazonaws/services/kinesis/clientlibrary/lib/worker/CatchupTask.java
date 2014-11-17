package com.amazonaws.services.kinesis.clientlibrary.lib.worker;

import com.amazonaws.services.kinesis.clientlibrary.exceptions.internal.NotCaughtUpException;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessor2;
import com.amazonaws.services.kinesis.clientlibrary.lib.checkpoint.SentinelCheckpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigInteger;

/**
 *
 */
public class CatchupTask implements ITask {

    private static final Log LOG = LogFactory.getLog(CatchupTask.class);
    private final ShardInfo shardInfo;
    private final IRecordProcessor2 recordProcessor;
    private final TaskType taskType = TaskType.CATCHUP;
    private final RecordProcessorCheckpointer recordProcessorCheckpointer;
    // Back off for this interval if we encounter a problem (exception)
    private final long backoffTimeMillis;

    /**
     * Constructor
     */
    CatchupTask(ShardInfo shardInfo,
            IRecordProcessor2 recordProcessor,
            RecordProcessorCheckpointer recordProcessorCheckpointer,
            long backoffTimeMillis) {
        this.shardInfo = shardInfo;
        this.recordProcessor = recordProcessor;
        this.recordProcessorCheckpointer = recordProcessorCheckpointer;
        this.backoffTimeMillis = backoffTimeMillis;
    }

    @Override
    public TaskResult call() {
        boolean applicationException = false;
        Exception exception = null;

        try {
            LOG.debug("Catchup ShardId " + shardInfo.getShardId());
            boolean isCaughtUp = false;
            String savedLargestPermittedCheckpointValue =
                    recordProcessorCheckpointer.getLargestPermittedCheckpointValue();
            try {
                LOG.debug("Calling the record processor catchup().");
                // Nothing has yet been passed to the client from the library, so there's no way to
                // enforce largest sequence number checks.  Disable during catchup by allowing any checkpoint
                // value.
                recordProcessorCheckpointer.setLargestPermittedCheckpointValue(SentinelCheckpoint.SHARD_END.toString());
                isCaughtUp = recordProcessor.catchup(recordProcessorCheckpointer);
                if (!isCaughtUp) {
                    LOG.debug("Record processor is NOT caught up.");
                    // Similar to BlockOnParentShardTask, use a magic unthrown exception value to indicate
                    // there is unfinished work for this task
                    exception = new NotCaughtUpException("Record processor is NOT caught up");
                } else {
                    LOG.debug("Record processor is caught up.");
                    return new TaskResult(null);
                }
            } catch (Exception e) {
                applicationException = true;
                throw e;
            } finally {
                // restore saved largest permitted checkpoint value, as it is used to reset the data fetcher
                // in ProcessTask in the event the shard iterator expires.
                recordProcessorCheckpointer.setLargestPermittedCheckpointValue(
                        savedLargestPermittedCheckpointValue);
            }
        } catch (Exception e) {
            if (applicationException) {
                LOG.error("Application catchup() threw exception: ", e);
            } else {
                LOG.error("Caught exception: ", e);
            }
            exception = e;
            // backoff if we encounter an exception
            try {
                Thread.sleep(this.backoffTimeMillis);
            } catch (InterruptedException ie) {
                LOG.debug("Interrupted sleep", ie);
            }
        }

        return new TaskResult(exception);
    }

    @Override
    public TaskType getTaskType() {
        return taskType;
    }
}
