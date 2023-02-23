package io.vertx.skeleton.evs.objects;

import io.vertx.skeleton.sql.models.BaseRecord;
import io.vertx.skeleton.sql.models.RepositoryRecord;

import java.time.Instant;

public record EventJournalOffSet(
  String consumer,
  Long idOffSet,
  Instant dateOffSet,
  BaseRecord baseRecord
) implements RepositoryRecord<EventJournalOffSet> {


  @Override
  public EventJournalOffSet with(BaseRecord persistedRecord) {
    return new EventJournalOffSet(consumer,  idOffSet, dateOffSet, persistedRecord);
  }

  public EventJournalOffSet withIdOffSet(Long maxEventId) {
    return new EventJournalOffSet(consumer, maxEventId, dateOffSet, baseRecord);
  }
}
