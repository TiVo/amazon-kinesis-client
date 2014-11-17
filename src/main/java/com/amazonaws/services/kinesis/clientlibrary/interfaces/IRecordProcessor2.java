package com.amazonaws.services.kinesis.clientlibrary.interfaces;

/**
 * The Amazon Kinesis Client Library will instantiate record processors to process data records fetched from Amazon
 * Kinesis.  This version adds support for getting the last checkpoint sequence number on initialize().  Note:
 * the IRecordProcessor.initialize() method will not be called if the record processor implements this interface.
 */
public interface IRecordProcessor2 extends IRecordProcessor {

    /**
     * Invoked by the Amazon Kinesis Client Library before data records are delivered to the RecordProcessor instance
     * (via processRecords).  Takes precedence over IRecordProcessor.initialize().  Note, no significant work should
     * be done in this method.  If extensive catchup tasks are needed, they should be done in the catchup() method.
     *
     * @param shardId The record processor will be responsible for processing records of this shard.
     * @param sequenceNumber The last sequence number checkpointed for this shard, or null if no checkpoint.
     */
    void initialize(String shardId, String sequenceNumber);

    /**
     * Invoked by the Amazon Kinesis Client Library after initialize() but before the first call to processRecords()
     * on the live stream.  Gives the record processor a chance to catch up from external storage outside of the
     * 24 hour Kinesis window.  This method will be called repeatedly so long as it continues to return false.
     * Implementers of this method should batch their catchup progress by both checkpointing periodically and
     * returning from this function periodically so that the Worker has the ability to shut down the record
     * processor should it lose its lease on the shard.
     * @param checkpointer RecordProcessor should use this instance to checkpoint their progress.
     * @return true iff the record processor is finished with catchup.
     */
    boolean catchup(IRecordProcessorCheckpointer checkpointer);
}
