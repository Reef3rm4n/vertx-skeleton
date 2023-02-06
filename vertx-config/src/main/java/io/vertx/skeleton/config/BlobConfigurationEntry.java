package io.vertx.skeleton.config;

import io.vertx.core.shareddata.Shareable;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.skeleton.models.Tenant;

public interface BlobConfigurationEntry extends Shareable {

  Tenant tenant();
  String name();
  Buffer data();
}
