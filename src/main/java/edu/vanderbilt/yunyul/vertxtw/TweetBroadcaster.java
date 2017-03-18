package edu.vanderbilt.yunyul.vertxtw;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.sockjs.*;
import lombok.Setter;

import static edu.vanderbilt.yunyul.vertxtw.TwitterWallVerticle.log;

public class TweetBroadcaster {
    private static final String TWEET_PREFIX = "tweet.";

    @Setter
    private TwitterStreamHandler twitterHandler;

    private final EventBus eventBus;
    private final Multiset<String> tagRegistrantCounts = HashMultiset.create();

    public TweetBroadcaster(Router router, Vertx vertx) {
        log("Initializing broadcaster...");

        this.eventBus = vertx.eventBus();

        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);

        // Allow tweet broadcasts
        PermittedOptions tweetPermitted = new PermittedOptions().setAddressRegex("tweet\\..+");
        BridgeOptions options = new BridgeOptions()
                // No incoming messages permitted
                .addOutboundPermitted(tweetPermitted);

        // Filter registrations and handle un-registrations
        sockJSHandler.bridge(options, be -> {
            if (be.type() == BridgeEventType.REGISTER) {
                String tag = getHashtag(be);
                if (tag != null && twitterHandler.trackTag(tag)) {
                    tagRegistrantCounts.add(tag);
                } else {
                    be.complete(false);
                    return;
                }
            } else if (be.type() == BridgeEventType.UNREGISTER) {
                // Disconnecting the socket also triggers this
                String tag = getHashtag(be);
                if (tag != null) {
                    tagRegistrantCounts.remove(tag);
                    if (!tagRegistrantCounts.contains(tag)) {
                        twitterHandler.untrackTag(tag);
                    }
                } else {
                    be.complete(false);
                    return;
                }
            }
            be.complete(true);
        });

        router.route("/bus/*").handler(sockJSHandler);
    }

    private String getHashtag(BridgeEvent be) {
        String address = be.getRawMessage().getString("address");
        if (address.startsWith(TWEET_PREFIX)) {
            return address.substring(TWEET_PREFIX.length()).toLowerCase();
        } else {
            return null;
        }
    }

    /**
     * Broadcasts the specified message to all channels associated with the specified tag.
     *
     * @param tag  Tag to broadcast the message to
     * @param text The message to send
     */
    public void broadcast(String tag, String text) {
        eventBus.publish(TWEET_PREFIX + tag, text);
    }
}
