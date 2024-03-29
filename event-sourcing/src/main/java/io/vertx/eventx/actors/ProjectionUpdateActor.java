package io.vertx.eventx.actors;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.eventx.objects.ProjectionWrapper;
import io.vertx.eventx.sql.Repository;
import io.vertx.eventx.storage.pg.models.*;
import io.vertx.eventx.task.TimerTaskConfiguration;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.eventx.Aggregate;
import io.vertx.eventx.sql.exceptions.NotFound;
import io.vertx.eventx.sql.models.BaseRecord;
import io.vertx.eventx.sql.models.EmptyQuery;
import io.vertx.eventx.sql.models.QueryOptions;
import io.vertx.eventx.task.TimerTask;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.groupingBy;

public class ProjectionUpdateActor<T extends Aggregate> implements TimerTask {

  private static final Logger logger = LoggerFactory.getLogger(ProjectionUpdateActor.class);
  private final List<ProjectionWrapper<T>> projections;
  private final ChannelProxy<T> proxy;
  private final Repository<EventRecordKey, EventRecord, EventRecordQuery> eventJournal;
  private final Repository<EventJournalOffSetKey, EventJournalOffSet, EmptyQuery> eventJournalOffset;
  private final Repository<ProjectionHistoryKey, ProjectionHistory, ProjectionHistoryQuery> projectionHistory;

  public ProjectionUpdateActor(
    final List<ProjectionWrapper<T>> projections,
    final ChannelProxy<T> proxy,
    final Repository<EventRecordKey, EventRecord, EventRecordQuery> eventJournal,
    final Repository<EventJournalOffSetKey, EventJournalOffSet, EmptyQuery> eventJournalOffset,
    final Repository<ProjectionHistoryKey, ProjectionHistory, ProjectionHistoryQuery> projectionHistory
  ) {
    this.projections = projections;
    this.proxy = proxy;
    this.eventJournal = eventJournal;
    this.eventJournalOffset = eventJournalOffset;
    this.projectionHistory = projectionHistory;
  }


  //todo

  // System must contain a projection specific command that is part of the framework it-self.
  // UpdateProjection
  //
  //   1.Command
  //  The command porpuse is to ask for a projection update, and must implement the following rules :
  //    - Command will only affect the framework specific fields that wrap the aggregateState.
  //    - ProjectionUpdated event must be appended to event log
  //    - ProjectionUpdated event must be filtered out of the events that are passed to EventBehaviour implementors.
  //
  //
  //  2.Issuer
  //  The issuer purpose is to generate UpdateProjection command's, and must be implemented as follows :
  //  - A schduled task with cluster-wide lock that consumes events via id off-set.
  //  - query for the event-polling must ignore system events like UpdateProjection
  //  - events are than groupped per entity and reduced into projection update commands
  //  - commands are sent to entities and result in Projection implementors to be triggered inside the EntityAggregateHandler it self.
  //  -
  //
  //  * Notes
  //  By doing this projections will have the following attributes :
  //      - decouple event log appends from projections
  //      - projections updates are partitioned per entity thus giving ability to concurrently update many entities and projections
  //      - projections updates will always contain the correct state and event offsets
  //
  @Override
  public Uni<Void> performTask() {
    return eventJournalOffset.selectByKey(new EventJournalOffSetKey(this.getClass().getName()))
      .onFailure(NotFound.class)
      .recoverWithUni(throwable -> eventJournalOffset.insert(new EventJournalOffSet(this.getClass().getName(), 0L, null, BaseRecord.newRecord())))
      .flatMap(eventJournalOffSet ->
        eventJournal.transaction(
          sqlConnection -> eventJournal.query(readJournalQuery(eventJournalOffSet))
            .flatMap(polledEvents -> Multi.createFrom().iterable(projections)
              .onItem().transformToUniAndMerge(projection -> handleProjection(projection, polledEvents, sqlConnection))
              .collect().asList()
              .replaceWith(polledEvents)
            )
            .flatMap(polledEvents -> eventJournalOffset.updateByKey(newOffset(eventJournalOffSet, polledEvents), sqlConnection))
        )
      )
      .replaceWithVoid();
  }

  @NotNull
  private static EventJournalOffSet newOffset(EventJournalOffSet eventJournalOffSet, List<EventRecord> polledEvents) {
    return eventJournalOffSet.withIdOffSet(polledEvents.stream().map(EventRecord::id).max(Comparator.naturalOrder()).orElseThrow());
  }


  private EventRecordQuery readJournalQuery(final EventJournalOffSet eventJournalOffSet) {
    return new EventRecordQuery(
      null,
      null,
      null,
      null,
      eventJournalOffSet.idOffSet(),
      null,
      null,
      new QueryOptions(
        "id",
        false,
        null,
        null,
        null,
        null,
        null,
        10000,
        null,
        null
      )
    );
  }


  private Uni<Void> handleProjection(ProjectionWrapper<?> projection, final List<EventRecord> events, SqlConnection sqlConnection) {
    final var groupedEvents = events.stream()
      .filter(
        event -> projection.projection().eventClasses() == null ||
          projection.projection().eventClasses().stream().anyMatch(clazz -> clazz.getName().equals(event.eventClass()))
      )
      .collect(groupingBy(EventRecord::entityId));
    if (!groupedEvents.isEmpty()) {
      // todo improve concurrency by adding a task-queue that triggers the entity update it self
      // this multi stream should be converted to tasks that get submitted to a job-queue so that computation can be distributed
      return Multi.createFrom().iterable(groupedEvents.entrySet())
        .onItem().transformToUniAndMerge(entityEvents -> projectionHistory.selectByKey(new ProjectionHistoryKey(entityEvents.getKey(), projection.projection().getClass().getName(), projection.projection().tenantID()), sqlConnection)
          .onFailure(NotFound.class)
          .recoverWithUni(() -> insertNewProjectionHistory(projection, entityEvents.getKey(), sqlConnection))
          .flatMap(history -> {
              final var maxEventId = entityEvents.getValue().stream().map(EventRecord::id).max(Comparator.naturalOrder()).orElseThrow();
              final var minEventId = entityEvents.getValue().stream().map(EventRecord::id).min(Comparator.naturalOrder()).orElseThrow();
              if (history.lastEventVersion() < maxEventId) {
                logger.info("Updating projection ->" + projection.projection().getClass().getName());
                return proxy.wakeUp(new AggregateKey(entityEvents.getKey(), entityEvents.getValue().stream().findFirst().orElseThrow().baseRecord().tenantId()))
                  .flatMap(aggregateState -> projection.update(
                      aggregateState,
                      entityEvents.getValue().stream()
                        .filter(event -> event.id() > minEventId) // filter out events that were already processed
                        .map(event -> getEvent(event.eventClass(), event.event()))
                        .toList()
                    )
                  )
                  .flatMap(avoid -> projectionHistory.updateByKey(history.incrementVersion(maxEventId), sqlConnection));
              } else {
                logger.info("Projection as already up to date -> " + projection.projection().getClass().getSimpleName());
                return Uni.createFrom().voidItem();
              }
            }
          )
        )
        .collect().asList()
        .replaceWithVoid();
    }
    return Uni.createFrom().voidItem();
  }

  private Uni<ProjectionHistory> insertNewProjectionHistory(ProjectionWrapper<?> projection, String entityId, SqlConnection sqlConnection) {
    return projectionHistory.insert(new ProjectionHistory(
        entityId,
        projection.projection().getClass().getName(),
        0L,
        BaseRecord.newRecord(projection.projection().tenantID())
      )
      , sqlConnection
    );
  }


  private Object getEvent(final String eventClazz, JsonObject event) {
    try {
      final var eventClass = Class.forName(eventClazz);
      return event.mapTo(eventClass);
    } catch (Exception e) {
      logger.error("Unable to cast event", e);
      throw new IllegalArgumentException("Unable to cast event");
    }
  }

  @Override
  public TimerTaskConfiguration configuration() {
    return TimerTask.super.configuration();
  }
}
