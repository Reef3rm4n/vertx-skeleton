package io.vertx.eventx;

import java.util.List;

public interface Behaviour<T extends Aggregate, C extends Command> {

  List<Object> process(T state, C command);
  default String tenantID() {
    return "default";
  }
}
