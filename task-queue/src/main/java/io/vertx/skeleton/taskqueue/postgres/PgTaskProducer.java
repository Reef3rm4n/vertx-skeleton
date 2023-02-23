package io.vertx.skeleton.taskqueue.postgres;

import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.skeleton.taskqueue.models.*;
import io.vertx.skeleton.taskqueue.postgres.mappers.MessageQueueMapper;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.skeleton.taskqueue.postgres.models.MessageRecord;
import io.vertx.skeleton.taskqueue.postgres.models.MessageRecordID;
import io.vertx.skeleton.taskqueue.postgres.models.MessageRecordQuery;
import io.vertx.skeleton.taskqueue.misc.FileSystemFallBack;
import io.vertx.skeleton.taskqueue.TaskProducer;
import io.vertx.skeleton.models.MessageState;
import io.vertx.skeleton.models.exceptions.OrmConnectionException;
import io.vertx.skeleton.sql.Repository;
import io.vertx.skeleton.sql.RepositoryHandler;
import io.vertx.skeleton.sql.models.BaseRecord;


import java.util.List;

public class PgTaskProducer implements TaskProducer {
  private final Repository<MessageRecordID, MessageRecord, MessageRecordQuery> queue;
  private final FileSystemFallBack fallback;

  public PgTaskProducer(RepositoryHandler repositoryHandler) {
    this.queue = new Repository<>(MessageQueueMapper.INSTANCE, repositoryHandler);
    this.fallback = new FileSystemFallBack(queue);
  }

  public <T> Uni<Void> enqueue(Message<T> message, TaskTransaction taskTransaction) {
    final var queueEntry = new MessageRecord(
      message.messageId(),
      message.scheduled(),
      message.expiration(),
      message.priority(),
      0,
      MessageState.CREATED,
      message.payload().getClass().getName(),
      JsonObject.mapFrom(message.payload()),
      null,
      null,
      BaseRecord.newRecord(message.tenant())
    );
    return queue.insert(queueEntry, (SqlConnection) taskTransaction.connection()).replaceWithVoid()
      .onFailure(OrmConnectionException.class).recoverWithUni(() -> fallback.load(queueEntry));
  }

  public <T> Uni<Void> enqueue(List<Message<T>> entries, TaskTransaction taskTransaction) {
    final var queueEntries = entries.stream().map(
      message -> new MessageRecord(
        message.messageId(),
        message.scheduled(),
        message.expiration(),
        message.priority(),
        0,
        MessageState.CREATED,
        message.payload().getClass().getName(),
        JsonObject.mapFrom(message.payload()),
        null,
        null,
        BaseRecord.newRecord(message.tenant())
      )
    ).toList();
    return queue.insertBatch(queueEntries, (SqlConnection) taskTransaction.connection()).replaceWithVoid()
      .onFailure(OrmConnectionException.class).recoverWithUni(() -> fallback.load(queueEntries));
  }

  public Uni<Void> cancel(MessageID messageID) {
    return queue.deleteByKey(new MessageRecordID(messageID.id(), messageID.tenant()));
  }

  public <T> Uni<Message<T>> get(MessageID messageID, Class<T> tClass) {
    return queue.selectByKey(new MessageRecordID(messageID.id(), messageID.tenant()))
      .map(entry -> new Message<>(
          entry.id(),
          entry.baseRecord().tenantId(),
          entry.scheduled(),
          entry.expiration(),
          entry.priority(),
          entry.payload().mapTo(tClass)
        )
      );
  }

  public <T> Uni<List<Message<T>>> query(MessageRecordQuery query, Class<T> tClass) {
    return queue.query(query)
      .map(entries -> entries.stream()
        .map(entry -> new Message<>(
          entry.id(),
          entry.baseRecord().tenantId(),
          entry.scheduled(),
          entry.expiration(),
          entry.priority(),
          entry.payload().mapTo(tClass)
        )).toList()
      );
  }

}
