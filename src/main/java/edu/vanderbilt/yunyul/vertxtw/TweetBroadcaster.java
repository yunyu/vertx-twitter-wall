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

    private final Object lock = new Object();
    private Multimap<String, SockJSSocket> channels = HashMultimap.create();
    private Multimap<SockJSSocket, String> clients = HashMultimap.create();

    public TweetBroadcaster(Router router, Vertx vertx) {
        log("Initializing broadcaster...");
        SockJSHandlerOptions options = new SockJSHandlerOptions().setHeartbeatInterval(2000);

        SockJSHandler sockJSHandler = SockJSHandler.create(vertx, options);

        sockJSHandler.socketHandler(sock -> {
            sock.handler(buffer -> {
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
                            synchronized (lock) {
                                channels.put(tag, sock);
                                clients.put(sock, tag);
                                twitterHandler.trackTag(tag);
                            }
                        }
                        break;
                    // Unregister command
                    case "UNREG":
                        removeClientFromTag(sock, tag);
                }

            });
            sock.endHandler(v -> {
                synchronized (lock) {
                    for (String channel : clients.get(sock)) {
                        channels.remove(channel, sock);
                    }
                    clients.removeAll(sock);
                }
            });
        });

        router.route("/sock/*").handler(sockJSHandler);
    }


    private void removeClientFromTag(SockJSSocket sock, String tag) {
        synchronized (lock) {
            channels.remove(tag, sock);
            clients.remove(sock, tag);
        }
        if (!channels.containsKey(tag)) {
            twitterHandler.untrackTag(tag);
        }
    }

    /**
     * Broadcasts the specified message to all channels associated with the specified tag
     * @param tag Tag to broadcast the message to
     * @param text The message to send
     */
    public void broadcast(String tag, String text) {
        channels.get(tag).forEach(client -> client.write(Buffer.buffer(text)));
    }
}
