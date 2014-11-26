package com.amazonaws.services.kinesis.clientlibrary.lib.worker;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Task to block processing of all data records in the shard until the timeout period for stealing a
 * currently non expired lease has expired
 */
public class BlockOnStolenShardTask implements ITask {

    private static final Log LOG = LogFactory.getLog(BlockOnStolenShardTask.class);
    private final TaskType taskType = TaskType.BLOCK_ON_STOLEN_SHARD;
    private final ShardInfo shardInfo;
    private final long stolenShardDelayMillis;

    public BlockOnStolenShardTask(ShardInfo shardInfo, long stolenShardDelayMillis) {
        this.shardInfo = shardInfo;
        this.stolenShardDelayMillis = stolenShardDelayMillis;
    }

    @Override
    public TaskResult call() {
        Exception exception = null;

        LOG.info("Sleeping for " + (stolenShardDelayMillis / 1000) + " seconds before processing stolen shard: "
                + shardInfo.getShardId());
        try {
            Thread.sleep(stolenShardDelayMillis);
        } catch (InterruptedException e) {
            LOG.error("Sleep interrupted when waiting for delay to expire");
            exception = e;
        }

        return new TaskResult(exception);
    }

    @Override
    public TaskType getTaskType() {
        return taskType;
    }
}
