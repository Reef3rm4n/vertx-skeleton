package io.vertx.eventx.domain;

import io.activej.inject.annotation.Inject;
import io.activej.inject.annotation.Provides;
import io.vertx.eventx.common.VertxComponent;
import io.vertx.eventx.domain.behaviours.ChangeBehaviour;
import io.vertx.eventx.domain.behaviours.ChangedAggregator;
import io.vertx.eventx.domain.behaviours.CreateAggregator;
import io.vertx.eventx.domain.behaviours.CreateBehaviour;

public class EventSourcingTestModule extends VertxComponent {


  @Provides
  @Inject
  ChangedAggregator changeData1Aggregator() {
    return new ChangedAggregator();
  }

  @Provides
  @Inject
  ChangeBehaviour changeData1BehaviourEntity() {
    return new ChangeBehaviour();
  }




  @Provides
  @Inject
  CreateBehaviour createEntityBehaviour() {
    return new CreateBehaviour();
  }

  @Provides
  @Inject
  CreateAggregator entityBehaviour(){
    return new CreateAggregator();
  }

}
