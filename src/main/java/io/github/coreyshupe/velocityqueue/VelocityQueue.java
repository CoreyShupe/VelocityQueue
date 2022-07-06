package io.github.coreyshupe.velocityqueue;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.gson.GsonConfigurationLoader;
import org.slf4j.Logger;

@Plugin(
    id = "velocity_queue",
    description = "Single-server queue for fallback-based queues.",
    authors = {
        "Corey Shupe (FiXed)"
    },
    name = "Velocity Queue",
    url = "https://github.com/CoreyShupe/VelocityQueue",
    version = "0.0.1"
)
public class VelocityQueue {
  private final ProxyServer server;
  private final Path directory;

  @Inject
  public VelocityQueue(ProxyServer server, @DataDirectory Path directory) {
    this.server = server;
    this.directory = directory;
  }

  @Subscribe
  public void onInitialize(ProxyInitializeEvent ignored) {
    if (Files.notExists(this.directory)) {
      try {
        Files.createDirectories(this.directory);
      } catch (IOException ioException) {
        throw new IllegalStateException(ioException);
      }
    } else if (!Files.isDirectory(this.directory)) {
      throw new IllegalStateException("The data directory for VelocityQueue cannot be a file.");
    }

    GsonConfigurationLoader loader = GsonConfigurationLoader.builder()
        .setPath(this.directory.resolve("config.json"))
        .build();

    ConfigurationNode root;
    try {
      root = loader.load();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    boolean shouldSave = false;

    ConfigurationNode queueServerNode = root.getNode("queue_server");
    String queueServer;
    if (queueServerNode.isEmpty()) {
      shouldSave = true;
      queueServerNode.setValue("queue");
      queueServer = "queue";
    } else {
      queueServer = queueServerNode.getString();
    }

    ConfigurationNode baseServerNode = root.getNode("base_server");
    String baseServer;
    if (baseServerNode.isEmpty()) {
      shouldSave = true;
      baseServerNode.setValue("hub");
      baseServer = "hub";
    } else {
      baseServer = baseServerNode.getString();
    }

    if (shouldSave) {
      try {
        loader.save(root);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    this.server.getServer(queueServer).ifPresentOrElse(
        server -> this.server.getServer(baseServer).ifPresentOrElse(
            serverDefault -> this.server.getEventManager()
                .register(VelocityQueue.this, new QueueManagement(this, this.server, server, serverDefault)),
            () -> {
              throw new IllegalStateException("Failed to find base server in registry.");
            }), () -> {
          throw new IllegalStateException("Failed to find queue server in registry.");
        });
  }
}
