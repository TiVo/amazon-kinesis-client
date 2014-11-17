package com.amazonaws.services.kinesis.clientlibrary.exceptions.internal;

import com.amazonaws.services.kinesis.clientlibrary.exceptions.KinesisClientLibRetryableException;

/**
 * Used internally in the Amazon Kinesis Client Library.  Indicates that we cannot start processing data for a shard
 * because the record processor has not yet finished catching up from its backing store.
 */
public class NotCaughtUpException extends KinesisClientLibRetryableException {

    /**
     * Constructor
     *
     * @param message Error message
     */
    public NotCaughtUpException(String message) {
        super(message);
    }

    /**
     * Constructor
     *
     * @param message Error message
     * @param e Cause of the exception
     */
    public NotCaughtUpException(String message, Exception e) {
        super(message, e);
    }
}
