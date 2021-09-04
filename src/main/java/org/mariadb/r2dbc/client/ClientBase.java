// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2020-2021 MariaDB Corporation Ab

package org.mariadb.r2dbc.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import io.r2dbc.spi.R2dbcTransientResourceException;
import io.r2dbc.spi.TransactionDefinition;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import org.mariadb.r2dbc.ExceptionFactory;
import org.mariadb.r2dbc.MariadbConnectionConfiguration;
import org.mariadb.r2dbc.MariadbTransactionDefinition;
import org.mariadb.r2dbc.message.ClientMessage;
import org.mariadb.r2dbc.message.Context;
import org.mariadb.r2dbc.message.ServerMessage;
import org.mariadb.r2dbc.message.client.QueryPacket;
import org.mariadb.r2dbc.message.client.QuitPacket;
import org.mariadb.r2dbc.message.client.SslRequestPacket;
import org.mariadb.r2dbc.message.server.InitialHandshakePacket;
import org.mariadb.r2dbc.util.HostAddress;
import org.mariadb.r2dbc.util.PrepareCache;
import org.mariadb.r2dbc.util.constants.ServerStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.concurrent.Queues;

public abstract class ClientBase implements Client {

  private static final Logger logger = Loggers.getLogger(ClientBase.class);
  protected final ReentrantLock lock = new ReentrantLock();
  private final MariadbConnectionConfiguration configuration;
  protected final Connection connection;
  protected final HostAddress hostAddress;
  protected final Queue<CmdElement> responseReceivers = Queues.<CmdElement>unbounded().get();
  private final AtomicBoolean isClosed = new AtomicBoolean(false);
  private final MariadbPacketDecoder mariadbPacketDecoder;
  private final MariadbPacketEncoder mariadbPacketEncoder = new MariadbPacketEncoder();
  protected volatile Context context;
  private final PrepareCache prepareCache;

  protected ClientBase(
      Connection connection,
      MariadbConnectionConfiguration configuration,
      HostAddress hostAddress) {
    this.connection = connection;
    this.configuration = configuration;
    this.hostAddress = hostAddress;
    this.prepareCache =
        new PrepareCache(
            this.configuration.useServerPrepStmts() ? this.configuration.getPrepareCacheSize() : 0,
            this);
    this.mariadbPacketDecoder = new MariadbPacketDecoder(responseReceivers, this);

    connection.addHandler(mariadbPacketDecoder);
    connection.addHandler(mariadbPacketEncoder);

    if (logger.isTraceEnabled()) {
      connection.addHandlerFirst(
          LoggingHandler.class.getSimpleName(),
          new LoggingHandler(ClientBase.class, LogLevel.TRACE));
    }

    connection
        .inbound()
        .receive()
        .doOnError(this::handleConnectionError)
        .doOnComplete(this::closedServlet)
        .then()
        .subscribe();
  }

  public static TcpClient setSocketOption(
      MariadbConnectionConfiguration configuration, TcpClient tcpClient) {
    if (configuration.getConnectTimeout() != null) {
      tcpClient =
          tcpClient.option(
              ChannelOption.CONNECT_TIMEOUT_MILLIS,
              Math.toIntExact(configuration.getConnectTimeout().toMillis()));
    }

    if (configuration.getSocketTimeout() != null) {
      tcpClient =
          tcpClient.option(
              ChannelOption.SO_TIMEOUT,
              Math.toIntExact(configuration.getSocketTimeout().toMillis()));
    }

    if (configuration.isTcpKeepAlive()) {
      tcpClient = tcpClient.option(ChannelOption.SO_KEEPALIVE, configuration.isTcpKeepAlive());
    }

    if (configuration.isTcpAbortiveClose()) {
      tcpClient = tcpClient.option(ChannelOption.SO_LINGER, 0);
    }
    return tcpClient;
  }

  private void handleConnectionError(Throwable throwable) {
    R2dbcNonTransientResourceException err;
    if (this.isClosed.compareAndSet(false, true)) {
      err =
          new R2dbcNonTransientResourceException("Connection unexpected error", "08000", throwable);
      logger.error("Connection unexpected error", throwable);
      Channel channel = this.connection.channel();
      if (!channel.isOpen()) {
        this.connection.dispose();
      }
    } else {
      err = new R2dbcNonTransientResourceException("Connection error", "08000", throwable);
      logger.error("Connection error", throwable);
    }
    clearWaitingListWithError(err);
  }

  @Override
  public Mono<Void> close() {
    return Mono.defer(
        () -> {
          if (this.isClosed.compareAndSet(false, true)) {

            Channel channel = this.connection.channel();
            if (!channel.isOpen()) {
              this.connection.dispose();
              return this.connection.onDispose();
            }

            return Flux.just(QuitPacket.INSTANCE)
                .doOnNext(message -> connection.channel().writeAndFlush(message))
                .then()
                .doOnSuccess(v -> this.connection.dispose())
                .then(this.connection.onDispose());
          }

          return Mono.empty();
        });
  }

  public Flux<ServerMessage> sendCommand(ClientMessage message) {
    return sendCommand(message, DecoderState.QUERY_RESPONSE);
  }

  @Override
  public Mono<Void> sendSslRequest(
      SslRequestPacket sslRequest, MariadbConnectionConfiguration configuration) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    try {
      SSLEngine engine =
          configuration.getSslConfig().getSslContext().newEngine(connection.channel().alloc());
      final SslHandler sslHandler = new SslHandler(engine);

      final GenericFutureListener<Future<? super Channel>> listener =
          configuration
              .getSslConfig()
              .getHostNameVerifier(
                  result,
                  this.hostAddress == null ? null : this.hostAddress.getHost(),
                  context.getThreadId(),
                  engine);

      sslHandler.handshakeFuture().addListener(listener);
      // send SSL request in clear
      connection.channel().writeAndFlush(sslRequest);

      // add SSL handler
      connection.addHandlerFirst(sslHandler);
      return Mono.fromFuture(result);

    } catch (SSLException | R2dbcTransientResourceException e) {
      result.completeExceptionally(e);
      return Mono.fromFuture(result);
    }
  }

  public Flux<ServerMessage> sendCommand(ClientMessage message, DecoderState initialState) {
    return sendCommand(message, initialState, null);
  }

  public abstract Flux<ServerMessage> sendCommand(
      ClientMessage message, DecoderState initialState, String sql);

  private Flux<ServerMessage> execute(Consumer<FluxSink<ServerMessage>> s) {
    AtomicBoolean atomicBoolean = new AtomicBoolean();
    return Flux.create(
        sink -> {
          if (!isConnected()) {
            sink.error(
                new R2dbcNonTransientResourceException(
                    "Connection is close. Cannot send anything"));
            return;
          }
          if (atomicBoolean.compareAndSet(false, true)) {
            try {
              lock.lock();
              s.accept(sink);
            } finally {
              lock.unlock();
            }
          }
        });
  }

  abstract void begin(FluxSink<ServerMessage> sink, String sql);

  abstract void executeWhenTransaction(FluxSink<ServerMessage> sink, String cmd);

  abstract void executeAutoCommit(FluxSink<ServerMessage> sink, boolean autoCommit);

  public long getThreadId() {
    return context.getThreadId();
  }

  /**
   * Specific implementation, to avoid executing BEGIN if already in transaction
   *
   * @return publisher
   */
  public Mono<Void> beginTransaction() {
    try {
      lock.lock();
      return execute(sink -> begin(sink, "BEGIN"))
          .handle(ExceptionFactory.withSql("BEGIN")::handleErrorResponse)
          .then();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Specific implementation, to avoid executing START TRANSACTION if already in transaction
   *
   * @return publisher
   */
  public Mono<Void> beginTransaction(TransactionDefinition definition) {
    StringBuilder sb = new StringBuilder("START TRANSACTION");
    boolean first = true;
    if (Boolean.TRUE.equals(definition.getAttribute(TransactionDefinition.READ_ONLY))) {
      sb.append(" READ ONLY");
      first = false;
    }
    if (Boolean.TRUE.equals(
        definition.getAttribute(MariadbTransactionDefinition.WITH_CONSISTENT_SNAPSHOT))) {
      if (!first) sb.append(",");
      sb.append(" WITH CONSISTENT SNAPSHOT");
    }

    String sql = sb.toString();
    try {
      lock.lock();
      return execute(sink -> begin(sink, sql))
          .handle(ExceptionFactory.withSql(sql)::handleErrorResponse)
          .then();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Specific implementation, to avoid executing COMMIT if no transaction
   *
   * @return publisher
   */
  public Mono<Void> commitTransaction() {
    try {
      lock.lock();
      return execute(sink -> executeWhenTransaction(sink, "COMMIT"))
          .handle(ExceptionFactory.withSql("COMMIT")::handleErrorResponse)
          .then();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Specific implementation, to avoid executing ROLLBACK if no transaction
   *
   * @return publisher
   */
  public Mono<Void> rollbackTransaction() {
    try {
      lock.lock();
      return execute(sink -> executeWhenTransaction(sink, "ROLLBACK"))
          .handle(ExceptionFactory.withSql("ROLLBACK")::handleErrorResponse)
          .then();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Specific implementation, to avoid executing ROLLBACK TO TRANSACTION if no transaction
   *
   * @return publisher
   */
  public Mono<Void> rollbackTransactionToSavepoint(String name) {
    try {
      lock.lock();
      String cmd = String.format("ROLLBACK TO SAVEPOINT `%s`", name.replace("`", "``"));
      return execute(sink -> executeWhenTransaction(sink, cmd))
          .handle(ExceptionFactory.withSql(cmd)::handleErrorResponse)
          .then();
    } finally {
      lock.unlock();
    }
  }

  public Mono<Void> releaseSavepoint(String name) {
    try {
      lock.lock();
      String cmd = String.format("RELEASE SAVEPOINT `%s`", name.replace("`", "``"));
      return sendCommand(new QueryPacket(cmd))
          .handle(ExceptionFactory.withSql(cmd)::handleErrorResponse)
          .then();
    } finally {
      lock.unlock();
    }
  }

  public Mono<Void> createSavepoint(String name) {
    try {
      lock.lock();
      String cmd = String.format("SAVEPOINT `%s`", name.replace("`", "``"));
      return sendCommand(new QueryPacket(cmd))
          .handle(ExceptionFactory.withSql(cmd)::handleErrorResponse)
          .then();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Specific implementation, to avoid changing autocommit mode if already in this autocommit mode
   *
   * @return publisher
   */
  public Mono<Void> setAutoCommit(boolean autoCommit) {
    try {
      lock.lock();
      return execute(sink -> executeAutoCommit(sink, autoCommit))
          .handle(ExceptionFactory.withSql(null)::handleErrorResponse)
          .then();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Flux<ServerMessage> receive(DecoderState initialState) {
    return Flux.create(
        sink -> {
          this.responseReceivers.add(new CmdElement(sink, initialState));
        });
  }

  public void setContext(InitialHandshakePacket handshake, long clientCapabilities) {
    this.context =
        new ContextImpl(
            handshake.getServerVersion(),
            handshake.getThreadId(),
            handshake.getCapabilities(),
            handshake.getServerStatus(),
            handshake.isMariaDBServer(),
            clientCapabilities);
    mariadbPacketDecoder.setContext(context);
    mariadbPacketEncoder.setContext(context);
  }

  /**
   * Get current server autocommit.
   *
   * @return autocommit current server value.
   */
  @Override
  public boolean isAutoCommit() {
    return (this.context.getServerStatus() & ServerStatus.AUTOCOMMIT) > 0;
  }

  @Override
  public boolean noBackslashEscapes() {
    return (this.context.getServerStatus() & ServerStatus.NO_BACKSLASH_ESCAPES) > 0;
  }

  @Override
  public ServerVersion getVersion() {
    return (this.context != null) ? this.context.getVersion() : ServerVersion.UNKNOWN_VERSION;
  }

  @Override
  public boolean isConnected() {
    if (this.isClosed.get()) {
      return false;
    }
    return this.connection.channel().isOpen();
  }

  private void closedServlet() {
    if (this.isClosed.compareAndSet(false, true)) {
      Channel channel = this.connection.channel();
      if (!channel.isOpen()) {
        this.connection.dispose();
      }
      clearWaitingListWithError(
          ExceptionFactory.INSTANCE.createException("Connection unexpectedly closed", "08000", -1));

    } else {
      clearWaitingListWithError(new R2dbcNonTransientResourceException("Connection closed"));
    }
  }

  private void clearWaitingListWithError(Throwable exception) {
    mariadbPacketDecoder.connectionError(exception);
    CmdElement response;
    while ((response = this.responseReceivers.poll()) != null) {
      response.getSink().error(exception);
    }
  }

  public MariadbConnectionConfiguration getConf() {
    return configuration;
  }

  public HostAddress getHostAddress() {
    return hostAddress;
  }

  public abstract void sendNext();

  public PrepareCache getPrepareCache() {
    return prepareCache;
  }

  @Override
  public String toString() {
    return "Client{isClosed=" + isClosed + ", context=" + context + '}';
  }
}
