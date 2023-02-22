package io.vertx.skeleton.taskqueue.postgres.mappers;


import io.vertx.skeleton.taskqueue.postgres.models.MessageRecord;
import io.vertx.skeleton.taskqueue.postgres.models.MessageRecordID;
import io.vertx.skeleton.taskqueue.postgres.models.MessageRecordQuery;
import io.vertx.skeleton.models.MessageState;
import io.vertx.skeleton.sql.RecordMapper;
import io.vertx.skeleton.sql.generator.filters.QueryBuilder;
import io.vertx.skeleton.sql.models.QueryFilter;
import io.vertx.skeleton.sql.models.QueryFilters;
import io.vertx.sqlclient.Row;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;


public class MessageQueueMapper implements RecordMapper<MessageRecordID, MessageRecord, MessageRecordQuery> {
  private static final String MESSAGE_ID = "message_id";
  private static final String SCHEDULED = "scheduled";
  private static final String EXPIRATION = "expiration";
  private static final String PRIORITY = "priority";
  private static final String RETRY_COUNTER = "retry_counter";
  private static final String STATE = "state";
  private static final String PAYLOAD = "payload";
  private static final String FAILURES = "failure";
  private static final String VERTICLE_ID = "verticle_id";
  private static final String PAYLOAD_CLASS = "payload_class";
  private static final String TASK_QUEUE = "task_queue";

  public static MessageQueueMapper INSTANCE = new MessageQueueMapper();
  private MessageQueueMapper(){}

  @Override
  public String table() {
    return TASK_QUEUE;
  }

  @Override
  public Set<String> columns() {
    return Set.of(MESSAGE_ID,PAYLOAD_CLASS, SCHEDULED, EXPIRATION, PRIORITY, RETRY_COUNTER, STATE, PAYLOAD, FAILURES, VERTICLE_ID);

  }


  @Override
  public Set<String> keyColumns() {
    return Set.of(MESSAGE_ID);
  }

  @Override
  public MessageRecord rowMapper(Row row) {
    return new MessageRecord(
      row.getString(MESSAGE_ID),
      row.getLocalDateTime(SCHEDULED) != null ? row.getLocalDateTime(SCHEDULED).toInstant(ZoneOffset.UTC) : null,
      row.getLocalDateTime(EXPIRATION) != null ? row.getLocalDateTime(EXPIRATION).toInstant(ZoneOffset.UTC) : null,
      row.getInteger(PRIORITY),
      row.getInteger(RETRY_COUNTER),
      MessageState.valueOf(row.getString(STATE)),
      row.getString(PAYLOAD_CLASS),
      row.getJsonObject(PAYLOAD),
      row.getJsonObject(FAILURES),
      row.getString(VERTICLE_ID),
      baseRecord(row)
    );
  }

  @Override
  public void params(Map<String, Object> params, MessageRecord messageRecord) {
    params.put(MESSAGE_ID, messageRecord.id());
    if (messageRecord.scheduled() != null) {
      params.put(SCHEDULED, LocalDateTime.ofInstant(messageRecord.scheduled(), ZoneOffset.UTC));
    }
    if (messageRecord.expiration() != null) {
      params.put(EXPIRATION, LocalDateTime.ofInstant(messageRecord.expiration(), ZoneOffset.UTC));
    }
    params.put(PRIORITY, messageRecord.priority());
    params.put(RETRY_COUNTER, messageRecord.retryCounter());
    params.put(STATE, messageRecord.messageState().name());
    params.put(PAYLOAD, messageRecord.payload());
    params.put(PAYLOAD_CLASS, messageRecord.payloadClass());
    if (messageRecord.failedProcessors() != null && !messageRecord.failedProcessors().isEmpty()) {
      params.put(FAILURES, messageRecord.failedProcessors());
    }
    params.put(VERTICLE_ID, messageRecord.verticleId());
  }

  @Override
  public void keyParams(Map<String, Object> params, MessageRecordID key) {
    params.put(MESSAGE_ID, key.id());
  }

  @Override
  public void queryBuilder(MessageRecordQuery query, QueryBuilder builder) {
    builder.iLike(
        new QueryFilters<>(String.class)
          .filterColumn(MESSAGE_ID)
          .filterParams(query.ids())
      )
      .iLike(
        new QueryFilters<>(MessageState.class)
        .filterColumn(STATE)
        .filterParams(query.states())
      )
      .from(
        new QueryFilter<>(Instant.class)
          .filterColumn(SCHEDULED)
          .filterParam(query.scheduledFrom())
      )
      .to(
        new QueryFilter<>(Instant.class)
        .filterColumn(SCHEDULED)
        .filterParam(query.scheduledTo())
      )
      .from(
        new QueryFilter<>(Integer.class)
          .filterColumn(RETRY_COUNTER)
          .filterParam(query.retryCounterFrom())
      )
      .to(
        new QueryFilter<>(Integer.class)
          .filterColumn(RETRY_COUNTER)
          .filterParam(query.retryCounterTo())
      )
      .from(
        new QueryFilter<>(Integer.class)
          .filterColumn(PRIORITY)
          .filterParam(query.priorityFrom())
      )
      .to(
        new QueryFilter<>(Integer.class)
          .filterColumn(PRIORITY)
          .filterParam(query.priorityTo())
      );
  }

}