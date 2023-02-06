package io.vertx.skeleton.config;

import io.vertx.core.shareddata.Shareable;
import io.vertx.skeleton.models.Tenant;

public record BlobConfigurationKey(
  String name,
  Tenant tenant
) implements Shareable {

}
