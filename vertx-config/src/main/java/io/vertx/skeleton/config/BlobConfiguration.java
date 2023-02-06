package io.vertx.skeleton.config;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.skeleton.models.PersistedRecord;
import io.vertx.skeleton.models.Tenant;
import io.vertx.skeleton.orm.Repository;
import io.vertx.skeleton.orm.RepositoryHandler;

import java.util.List;

public class BlobConfiguration<T extends BlobConfigurationEntry> {
  private final Vertx vertx;
  private final Repository<ConfigurationKey, BlobConfigurationRecord, ConfigurationQuery> repository;
  private final Class<T> tClass;

  public BlobConfiguration(
    final Class<T> tClass,
    RepositoryHandler repositoryHandler
  ) {
    this.tClass = tClass;
    this.vertx = repositoryHandler.vertx();
    this.repository = new Repository<>(BlobConfigurationRecordMapper.INSTANCE, repositoryHandler);
  }

  public Buffer get(String name, Tenant tenant) {
    return vertx.sharedData().<ConfigurationKey, Buffer>getLocalMap(BlobConfigurationRecord.class.getName())
      .get(new ConfigurationKey(name, tClass.getName(), tenant));
  }

  public Uni<Void> add(T data) {
    return repository.insert(new BlobConfigurationRecord(data.name(), data.data(), PersistedRecord.newRecord(data.tenant())))
      .replaceWithVoid();
  }

  public Uni<Void> addAll(List<? extends BlobConfigurationEntry> configurationEntries) {
    final var entries = configurationEntries.stream().map(
      data -> new BlobConfigurationRecord(data.name(), data.data(), PersistedRecord.newRecord(data.tenant()))
    ).toList();
    return repository.insertBatch(entries);
  }

  public Uni<Void> update(T value) {
    return repository.updateByKey(new BlobConfigurationRecord(value.name(), value.data(), PersistedRecord.newRecord(value.tenant())))
      .replaceWithVoid();
  }

  public Uni<Void> delete(String name, Tenant tenant) {
    return repository.deleteByKey(new ConfigurationKey(name, tClass.getName(), tenant));
  }

}
