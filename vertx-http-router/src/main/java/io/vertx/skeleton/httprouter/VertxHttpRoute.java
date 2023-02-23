package io.vertx.skeleton.httprouter;

import io.vertx.skeleton.models.Error;
import io.vertx.skeleton.models.PublicQueryOptions;
import io.vertx.skeleton.models.RequestHeaders;
import io.vertx.skeleton.models.RequestMetadata;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.RoutingContext;

import java.time.Instant;
import java.util.List;

public interface VertxHttpRoute {
  void registerRoutes(Router router);

  default void ok(RoutingContext routingContext, Object o) {
    routingContext.response().setStatusCode(200)
      .putHeader(Constants.CONTENT_TYPE, Constants.APPLICATION_JSON)
      .sendAndForget(JsonObject.mapFrom(o).encode());
  }

  default void created(RoutingContext routingContext, Object o) {
    routingContext.response().setStatusCode(201)
      .putHeader(Constants.CONTENT_TYPE, Constants.APPLICATION_JSON)
      .sendAndForget(JsonObject.mapFrom(o).encode());
  }

  default void ok(RoutingContext routingContext, JsonObject o) {
    routingContext.response().setStatusCode(200)
      .putHeader(Constants.CONTENT_TYPE, Constants.APPLICATION_JSON)
      .sendAndForget(o.encode());
  }

  default void okWithArrayBody(RoutingContext routingContext, JsonArray jsonArray) {
    routingContext.response().setStatusCode(200)
      .putHeader(Constants.CONTENT_TYPE, Constants.APPLICATION_JSON)
      .sendAndForget(jsonArray.encode());
  }

  default void created(RoutingContext routingContext) {
    routingContext.response().setStatusCode(201).sendAndForget();
  }

  default void accepted(RoutingContext routingContext) {
    routingContext.response().setStatusCode(202).sendAndForget();
  }

  default void noContent(RoutingContext routingContext) {
    routingContext.response().setStatusCode(204).sendAndForget();
  }

  default void respondWithServerManagedError(RoutingContext routingContext, Error error) {
    routingContext.response()
      .setStatusCode(error.errorCode())
      .putHeader(Constants.CONTENT_TYPE, Constants.APPLICATION_JSON)
      .endAndForget(JsonObject.mapFrom(error).encode());
  }


  default void respondWithUnmanagedError(RoutingContext routingContext, Throwable throwable) {
    final var cause = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
    routingContext.response()
      .setStatusCode(500)
      .putHeader(Constants.CONTENT_TYPE, Constants.APPLICATION_JSON)
      .endAndForget(
        JsonObject.mapFrom(new Error(throwable.getMessage(), cause, 500)).encode()
      );
  }

  default <T> T extractRequestObject(Class<T> clazz, RoutingContext routingContext) {
    try {
      final var json = routingContext.body().asJsonObject();
      return json.mapTo(clazz);
    } catch (Exception e) {
      throw new RouterException(e.getMessage(), "malformed request, please check that your json conforms with notifier models", 500);
    }
  }

  default <T> List<T> extractRequestArray(Class<T> clazz, RoutingContext routingContext) {
    try {
      return routingContext.body().asJsonArray().stream().map(o -> JsonObject.mapFrom(o).mapTo(clazz)).toList();
    } catch (Exception e) {
      throw new RouterException(e.getMessage(), "malformed request, please check that your json conforms with notifier models", 500);
    }
  }

  default RequestMetadata extractMetadata(RoutingContext routingContext) {
    return new RequestMetadata(
      routingContext.request().getHeader(RequestMetadata.CLIENT_ID),
      routingContext.request().getHeader(RequestMetadata.CHANNEL_ID),
      routingContext.request().getHeader(RequestMetadata.EXT_SYSTEM_ID),
      routingContext.request().getHeader(RequestMetadata.X_TXT_ID),
      routingContext.request().getHeader(RequestMetadata.TXT_DATE),
      Integer.parseInt(routingContext.request().getHeader(RequestMetadata.BRAND_ID_HEADER)),
      Integer.parseInt(routingContext.request().getHeader(RequestMetadata.PARTNER_ID_HEADER)),
      routingContext.request().getHeader(RequestMetadata.PLAYER_ID),
      routingContext.request().getHeader(RequestMetadata.LONG_TERM_TOKEN)
    );
  }

  default RequestHeaders extractHeaders(RoutingContext routingContext) {
    return new RequestHeaders(
      routingContext.request().getHeader(RequestHeaders.REQUEST_ID),
      routingContext.request().getHeader(RequestHeaders.TENANT_ID),
      routingContext.request().getHeader(RequestHeaders.USER_ID),
      routingContext.request().getHeader(RequestHeaders.TOKEN)
    );
  }

  default RequestMetadata extractMetadataOrNull(RoutingContext routingContext) {
    if (routingContext.request().getHeader(RequestMetadata.PARTNER_ID_HEADER) != null && routingContext.request().getHeader(RequestMetadata.BRAND_ID_HEADER) != null) {
      return new RequestMetadata(
        routingContext.request().getHeader(RequestMetadata.CLIENT_ID),
        routingContext.request().getHeader(RequestMetadata.CHANNEL_ID),
        routingContext.request().getHeader(RequestMetadata.EXT_SYSTEM_ID),
        routingContext.request().getHeader(RequestMetadata.X_TXT_ID),
        routingContext.request().getHeader(RequestMetadata.TXT_DATE),
        Integer.parseInt(routingContext.request().getHeader(RequestMetadata.BRAND_ID_HEADER)),
        Integer.parseInt(routingContext.request().getHeader(RequestMetadata.PARTNER_ID_HEADER)),
        routingContext.request().getHeader(RequestMetadata.PLAYER_ID),
        routingContext.request().getHeader(RequestMetadata.LONG_TERM_TOKEN)
      );
    }
    return null;
  }

  default PublicQueryOptions getQueryOptions(RoutingContext routingContext) {
    final var desc = routingContext.queryParam("desc").stream().findFirst();
    final var creationDateFrom = routingContext.queryParam("creationDateFrom").stream().findFirst().map(Instant::parse);
    final var creationDateTo = routingContext.queryParam("creationDateTo").stream().findFirst().map(Instant::parse);
    final var lastUpdateFrom = routingContext.queryParam("lastUpdateFrom").stream().findFirst().map(Instant::parse);
    final var lastUpdateTo = routingContext.queryParam("lastUpdateTo").stream().findFirst().map(Instant::parse);
    final var pageNumber = routingContext.queryParam("pageNumber").stream().findFirst().map(Integer::parseInt);
    final var pageSize = routingContext.queryParam("pageSize").stream().findFirst().map(Integer::parseInt);
    pageSize.ifPresent(
      pSize -> {
        if (pSize > 1000) {
          throw new RouterException("Page size can't be greater than 5000", "", 400);
        }
      }
    );
    return new PublicQueryOptions(
      Boolean.parseBoolean(desc.orElse("false")),
      creationDateFrom.orElse(null),
      creationDateTo.orElse(null),
      lastUpdateFrom.orElse(null),
      lastUpdateTo.orElse(null),
      pageNumber.orElse(0),
      pageSize.orElse(1000)
    );
  }
}
