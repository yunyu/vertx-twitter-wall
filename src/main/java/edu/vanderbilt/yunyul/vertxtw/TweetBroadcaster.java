package edu.vanderbilt.yunyul.vertxtw;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.sockjs.*;
import lombok.Setter;

import static edu.vanderbilt.yunyul.vertxtw.TwitterWallBootstrap.log;

public class TweetBroadcaster {
    @Setter
    private TwitterHandler twitterHandler;

    private final Vertx vertx;
    private Multimap<String, String> clients = HashMultimap.create();

    public TweetBroadcaster(Router router, Vertx vertx) {
        this.vertx = vertx;
        log("Initializing broadcaster...");
        SockJSHandlerOptions options = new SockJSHandlerOptions().setHeartbeatInterval(2000);

        SockJSHandler sockJSHandler = SockJSHandler.create(vertx, options);

        sockJSHandler.socketHandler(sock -> {
            sock.handler(buffer -> {
                String client = sock.writeHandlerID();
                String msg = buffer.toString();

                String[] parts = msg.split(" ");

                String cmd = parts[0];
                String tag = parts[1];

                // Strip leading hashtag
                if (tag.startsWith("#")) {
                    tag = tag.substring(1);
                }

                switch (cmd.toUpperCase()) {
                    // Registration command
                    case "REG":
                        if (tag.length() > 0 && tag.length() <= 120) {
                            clients.put(tag, client);
                            twitterHandler.trackTag(tag);
                        }
                        break;
                    // Unregister command
                    case "UNREG":
                        removeClientFromTag(client, tag);
                }

                String finalTag = tag;
                sock.endHandler(v -> removeClientFromTag(client, finalTag));
            });
        });

        router.route("/sock/*").handler(sockJSHandler);
    }


    private void removeClientFromTag(String client, String tag) {
        clients.remove(tag, client);
        if (!clients.containsKey(tag)) {
            twitterHandler.untrackTag(tag);
        }
    }

    /**
     * Broadcasts the specified message to all clients associated with the specified tag
     * @param tag Tag to broadcast the message to
     * @param text The message to send
     */
    public void broadcast(String tag, String text) {
        clients.get(tag).forEach(client -> vertx.eventBus().publish(client, Buffer.buffer(text)));
    }
}
