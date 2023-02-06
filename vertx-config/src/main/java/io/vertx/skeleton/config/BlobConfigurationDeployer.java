package io.vertx.skeleton.config;

import io.activej.inject.Injector;
import io.smallrye.mutiny.Uni;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.mutiny.config.ConfigRetriever;
import io.vertx.mutiny.pgclient.pubsub.PgSubscriber;
import io.vertx.skeleton.models.Tenant;
import io.vertx.skeleton.orm.LiquibaseHandler;
import io.vertx.skeleton.orm.Repository;
import io.vertx.skeleton.orm.RepositoryHandler;
import io.vertx.skeleton.utils.CustomClassLoader;

import java.util.List;
import java.util.Map;

public class BlobConfigurationDeployer {


  private static final Logger LOGGER = LoggerFactory.getLogger(BlobConfigurationDeployer.class);
  public List<ConfigRetriever> listeners;
  public PgSubscriber pgSubscriber;


  public Uni<Injector> deploy(Injector injector, RepositoryHandler repositoryHandler) {
    final var repository = new Repository<>(BlobConfigurationRecordMapper.INSTANCE, repositoryHandler);
    if (CustomClassLoader.checkPresence(injector, BlobConfiguration.class)) {
      return bootstrapBlobConfiguration(injector, repositoryHandler, repository);
    }
    if (CustomClassLoader.checkPresence(injector, KubernetesSecret.class)) {

    }
    return Uni.createFrom().item(injector);
  }


  private Uni<Injector> bootstrapBlobConfiguration(Injector injector, RepositoryHandler repositoryHandler, Repository<ConfigurationKey, BlobConfigurationRecord, ConfigurationQuery> repository) {
    this.pgSubscriber = PgSubscriber.subscriber(repositoryHandler.vertx(),
      RepositoryHandler.connectionOptions(repositoryHandler.configuration())
    );
    pgSubscriber.reconnectPolicy(integer -> 0L);
    final var pgChannel = pgSubscriber.channel("configuration_channel");
    pgChannel.handler(id -> {
          final ConfigurationKey key = configurationKey(id);
          LOGGER.info("Updating configuration -> " + key);
          repository.selectByKey(key)
            .onItemOrFailure().transform((item, failure) -> handleMessage(repository, key, item, failure))
            .subscribe()
            .with(
              item -> LOGGER.info("Configuration updated -> " + key),
              throwable -> LOGGER.error("Unable to synchronize configuration -> " + key, throwable)
            );
        }
      )
      .endHandler(() -> subscriptionStopped(pgSubscriber))
      .subscribeHandler(BlobConfigurationDeployer::handleSubscription)
      .exceptionHandler(BlobConfigurationDeployer::handleSubscriberError);
    return liquibase(repositoryHandler)
      .call(avoid -> warmCaches(repositoryHandler, repository))
      .call(avoid -> pgSubscriber.connect())
      .replaceWith(injector);
  }

  private static Object handleMessage(final Repository<BlobConfigurationKey, BlobConfigurationRecord, ConfigurationQuery> repository, final ConfigurationKey key, final BlobConfiguration item, final Throwable failure) {
    if (failure != null) {
      LOGGER.info("Deleting configuration from cache -> " + key);
      return repository.repositoryHandler().vertx()
        .sharedData().getLocalMap(BlobConfigurationRecord.class.getName())
        .remove(key);
    } else {
      LOGGER.info("Loading new configuration into cache -> " + key);
      return repository.repositoryHandler().vertx()
        .sharedData().getLocalMap(BlobConfigurationRecord.class.getName())
        .put(key, item.data());
    }
  }


  private static ConfigurationKey configurationKey(final String id) {
    LOGGER.info("Parsing configuration channel message -> " + id);
    final var splittedId = id.split("::");
    final var name = splittedId[0];
    final var tClass = splittedId[1];
    final var tenant = new Tenant(Integer.parseInt(splittedId[2]), Integer.parseInt(splittedId[3]));
    return new ConfigurationKey(name, tClass, tenant);
  }

  private static Uni<Void> warmCaches(final RepositoryHandler repositoryHandler, final Repository<ConfigurationKey, BlobConfigurationRecord, ConfigurationQuery> repository) {
    return repository.stream(null,
      configurationRecord -> {
        final var cache = repositoryHandler.vertx().sharedData().<ConfigurationKey, Object>getLocalMap(ConfigurationRecord.class.getName());
        final var key = new BlobConfigurationKey(configurationRecord.name(), configurationRecord.persistedRecord().tenant());
        LOGGER.info("Loading configuration into cache -> " + key);
        cache.put(key, mapData(configurationRecord));
      }
    );
  }

  private static Uni<Void> liquibase(final RepositoryHandler repositoryHandler) {
    return LiquibaseHandler.liquibaseString(
      repositoryHandler,
      "config.xml",
      Map.of("schema", repositoryHandler.configuration().getString("schema"))
    );
  }

  private static void handleSubscriberError(final Throwable throwable) {
    LOGGER.error("PgSubscriber had to drop throwable", throwable);
  }

  private static void handleSubscription() {
    LOGGER.info("PgSubscribed to channel");
  }

  private static void subscriptionStopped(final PgSubscriber pgSubscriber) {
    LOGGER.info("Pg subscription dropped");
//    pgSubscriber.connectAndForget();
  }

  private static Bu mapData(final ConfigurationRecord item) {
    try {
      return item.data().mapTo(Class.forName(item.tClass()));
    } catch (ClassNotFoundException e) {
      LOGGER.error("Unable to cast configuration using class for name");
      throw new IllegalArgumentException(e);
    }
  }
}
