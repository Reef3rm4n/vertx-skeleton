package io.vertx.eventx.storage.pg.models;

import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Shareable;
import io.vertx.eventx.sql.models.BaseRecord;
import io.vertx.eventx.sql.models.RepositoryRecord;

import java.util.Objects;

public record EventRecord(
  Long id,
  String entityId,
  String eventClass,
  Long eventVersion,
  JsonObject event,
  JsonObject command,
  String commandClass,
  BaseRecord baseRecord
) implements RepositoryRecord<EventRecord>, Shareable {


  public EventRecord(String entityId, String eventClass, Long eventVersion, JsonObject event, JsonObject command, String commandClass, BaseRecord baseRecord) {
    this(null, entityId, eventClass, eventVersion, event, command, commandClass, baseRecord);
  }

  public EventRecord {
    Objects.requireNonNull(entityId, "Entity must not be null");
    Objects.requireNonNull(eventClass, "Event class must not be null");
    Objects.requireNonNull(eventVersion, "Event version must not be null");
    if (eventVersion < 0) {
      throw new IllegalArgumentException("Event version must be greater than 0");
    }
    Objects.requireNonNull(commandClass, "Command class must not be null");
    Objects.requireNonNull(event);
  }


  @Override
  public EventRecord with(BaseRecord baseRecord) {
    return new EventRecord(entityId, eventClass, eventVersion, event, command, commandClass, baseRecord);
  }
}
