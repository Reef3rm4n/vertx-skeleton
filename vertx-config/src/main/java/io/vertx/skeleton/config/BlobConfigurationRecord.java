package io.vertx.skeleton.config;

import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.skeleton.models.PersistedRecord;
import io.vertx.skeleton.models.RepositoryRecord;

public record BlobConfigurationRecord(
  String name,
  Buffer data,
  PersistedRecord persistedRecord
) implements RepositoryRecord<BlobConfigurationRecord> {

  @Override
  public BlobConfigurationRecord with(final PersistedRecord persistedRecord) {
    return new BlobConfigurationRecord(name, data, persistedRecord);
  }

}
