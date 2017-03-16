package edu.vanderbilt.yunyul.vertxtw;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.sockjs.*;
import lombok.Setter;

import java.util.regex.Pattern;

import static edu.vanderbilt.yunyul.vertxtw.TwitterWallBootstrap.log;

public class TweetBroadcaster {
    private static final Pattern hashtag = Pattern.compile("^\\w+$");

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
                String channel = parts[1].toLowerCase();

                switch (cmd.toUpperCase()) {
                    // Registration command
                    case "REG":
                        if (channel.length() > 0 && channel.length() <= 30 && hashtag.matcher(channel).matches()) {
                            synchronized (lock) {
                                channels.put(channel, sock);
                                clients.put(sock, channel);
                            }
                            twitterHandler.trackTag(channel);
                        }
                        break;
                    // Unregister command
                    case "UNREG":
                        removeClientFromTag(sock, channel);
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


    private void removeClientFromTag(SockJSSocket sock, String channel) {
        synchronized (lock) {
            channels.remove(channel, sock);
            clients.remove(sock, channel);
        }
        if (!channels.containsKey(channel)) {
            twitterHandler.untrackTag(channel);
        }
    }

    /**
     * Broadcasts the specified message to all channels associated with the specified tag
     * @param tag Tag to broadcast the message to
     * @param text The message to send
     */
    public void broadcast(String tag, String text) {
        channels.get(tag.toLowerCase()).forEach(client -> client.write(Buffer.buffer(text)));
    }
}
