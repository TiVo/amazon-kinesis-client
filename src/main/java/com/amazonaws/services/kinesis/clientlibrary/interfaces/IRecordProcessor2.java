package com.amazonaws.services.kinesis.clientlibrary.interfaces;

/**
 * The Amazon Kinesis Client Library will instantiate record processors to process data records fetched from Amazon
 * Kinesis.  This version adds support for getting the last checkpoint sequence number on initialize().  Note:
 * the IRecordProcessor.initialize() method will not be called if the record processor implements this interface.
 */
public interface IRecordProcessor2 extends IRecordProcessor {

    /**
     * Invoked by the Amazon Kinesis Client Library before data records are delivered to the RecordProcessor instance
     * (via processRecords).  Takes precedence over IRecordProcessor.initialize().
     *
     * @param shardId The record processor will be responsible for processing records of this shard.
     * @param sequenceNumber The last sequence number checkpointed for this shard, or null if no checkpoint.
     */
    void initialize(String shardId, String sequenceNumber);

}
