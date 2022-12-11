package io.vertx.skeleton.config;

import io.vertx.skeleton.models.PersistedRecord;
import io.vertx.skeleton.models.QueryOptions;
import io.vertx.skeleton.models.Tenant;
import io.vertx.skeleton.orm.Repository;
import io.vertx.skeleton.orm.RepositoryHandler;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Configuration<T extends ConfigurationEntry> {
  private final Vertx vertx;
  private final Class<T> tClass;
  private final Repository<ConfigurationKey, ConfigurationRecord, ConfigurationQuery> repository;

  public Configuration(
    final Class<T> tClass,
    RepositoryHandler repositoryHandler
  ) {
    this.tClass = tClass;
    this.vertx = repositoryHandler.vertx();
    this.repository = new Repository<>(ConfigurationRecordMapper.INSTANCE, repositoryHandler);
  }

  public T get(String name, Tenant tenant) {
    return vertx.sharedData().<ConfigurationKey, T>getLocalMap(ConfigurationRecord.class.getName())
      .get(new ConfigurationKey(name, tClass.getName(), tenant));
  }

  public Uni<T> add(T data) {
    return repository.insert(new ConfigurationRecord(data.name(), data.getClass().getName(), JsonObject.mapFrom(data), PersistedRecord.newRecord(data.tenant())))
      .map(configuration -> configuration.data().mapTo(tClass));
  }

  public Uni<Void> addAll(List<? extends ConfigurationEntry> configurationEntries) {
    final var entries = configurationEntries.stream().map(
      data -> new ConfigurationRecord(data.name(), data.getClass().getName(), JsonObject.mapFrom(data), PersistedRecord.newRecord(data.tenant()))
    ).toList();
    return repository.insertBatch(entries);
  }

  public Uni<Void> updateAll(List<? extends ConfigurationEntry> configurationEntries) {
    final var entries = configurationEntries.stream().map(
      data -> new ConfigurationRecord(data.name(), data.getClass().getName(), JsonObject.mapFrom(data), PersistedRecord.newRecord(data.tenant()))
    ).toList();
    return repository.updateByKeyBatch(entries);
  }

  public Uni<T> update(T value) {
    return repository.updateByKey(new ConfigurationRecord(value.name(), value.getClass().getName(), JsonObject.mapFrom(value), PersistedRecord.newRecord(value.tenant())))
      .map(configuration -> configuration.data().mapTo(tClass));
  }

  public Uni<Void> delete(String name, Tenant tenant) {
    return repository.deleteByKey(new ConfigurationKey(name, tClass.getName(), tenant));
  }

  public Set<Map.Entry<ConfigurationKey, T>> entrySet() {
    return vertx.getDelegate().sharedData().<ConfigurationKey, T>getLocalMap(ConfigurationRecord.class.getName()).entrySet();
  }

  public Set<ConfigurationKey> keySet() {
    return vertx.getDelegate().sharedData().<ConfigurationKey, T>getLocalMap(ConfigurationRecord.class.getName()).keySet();
  }

  public Uni<List<T>> query(List<String> names, QueryOptions queryOptions) {
    final var query = new ConfigurationQuery(names, List.of(tClass.getName()), queryOptions);
    return repository.query(query).map(configs -> configs.stream().map(cfg -> cfg.data().mapTo(tClass)).toList());
  }
}
