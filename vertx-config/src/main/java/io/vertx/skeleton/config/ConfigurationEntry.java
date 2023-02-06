package io.vertx.skeleton.config;

import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.skeleton.models.Tenant;
import io.vertx.core.shareddata.Shareable;

import java.util.Optional;

public interface ConfigurationEntry extends Shareable {

  Tenant tenant();

  String name();
}
