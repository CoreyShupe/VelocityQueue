package io.github.coreyshupe.velocityqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class QueueManagement implements Runnable {
  private final VelocityQueue plugin;
  private final ProxyServer proxyServer;
  private final RegisteredServer queueServer;
  private final RegisteredServer baseServer;
  private final Map<UUID, RegisteredServer> registeredServerReturnQueue;

  public QueueManagement(VelocityQueue plugin, ProxyServer proxyServer, RegisteredServer queueServer,
                         RegisteredServer baseServer) {
    this.plugin = plugin;
    this.proxyServer = proxyServer;
    this.queueServer = queueServer;
    this.registeredServerReturnQueue = new ConcurrentHashMap<>();
    this.baseServer = baseServer;

    this.proxyServer.getScheduler().buildTask(plugin, this).schedule();
  }

  @Subscribe
  public void initialPick(PlayerChooseInitialServerEvent event) {
    this.registeredServerReturnQueue.put(event.getPlayer().getUniqueId(), this.baseServer);
    event.setInitialServer(this.queueServer);
  }

  @SuppressWarnings("UnstableApiUsage")
  @Subscribe
  public void onPostConnect(ServerPostConnectEvent event) {
    if (event.getPreviousServer() == null) {
      return;
    }

    Player player = event.getPlayer();
    RegisteredServer previousServer = event.getPreviousServer();
    RegisteredServer currentServer = player.getCurrentServer().orElseThrow().getServer();

    if (this.queueServer.getServerInfo().getName().equals(currentServer.getServerInfo().getName())) {
      this.registeredServerReturnQueue.computeIfAbsent(player.getUniqueId(), ignored -> previousServer);
    }
  }

  @Override
  public void run() {
    CompletableFuture.allOf(this.registeredServerReturnQueue.entrySet().stream().map(entry -> {
      if (QueueManagement.this.proxyServer.getPlayer(entry.getKey()).isEmpty()) {
        QueueManagement.this.registeredServerReturnQueue.remove(entry.getKey());
        return null;
      }
      return entry.getValue().ping()
          .thenCompose(ping -> QueueManagement.this.proxyServer.getPlayer(entry.getKey()).map(
              player -> player.createConnectionRequest(entry.getValue()).connect()
                  .thenAccept(result -> {
                    if (result != null && result.isSuccessful()) {
                      QueueManagement.this.registeredServerReturnQueue.remove(entry.getKey());
                    }
                  })
          ).orElseGet(() -> {
            QueueManagement.this.registeredServerReturnQueue.remove(entry.getKey());
            return CompletableFuture.completedFuture(null);
          }));
    }).filter(Objects::nonNull).toArray(CompletableFuture[]::new)).whenComplete(
        (ignored1, ignored2) -> QueueManagement.this.proxyServer.getScheduler()
            .buildTask(QueueManagement.this.plugin, QueueManagement.this).delay(Duration.ofSeconds(2)).schedule());
  }
}
