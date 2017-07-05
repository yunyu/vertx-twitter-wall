package edu.vanderbilt.yunyul.vertxtw;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.sockjs.*;
import lombok.Setter;

import static edu.vanderbilt.yunyul.vertxtw.TwitterWallVerticle.log;

public class TweetBroadcaster {
    private static final String TWEET_PREFIX = "tweet.";

    @Setter
    private TwitterStreamHandler twitterStreamHandler;

    private final Vertx vertx;
    private final EventBus eventBus;
    private final Multiset<String> tagRegistrantCounts = HashMultiset.create();
    private final CircuitBreaker messagePublish;

    public TweetBroadcaster(Router router, Vertx vertx) {
        log("Initializing broadcaster...");

        this.vertx = vertx;
        this.eventBus = vertx.eventBus();
        this.messagePublish = CircuitBreaker.create("message-publish", vertx);

        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);

        // Allow tweet broadcasts
        PermittedOptions tweetPermitted = new PermittedOptions().setAddressRegex("tweet\\..+");
        BridgeOptions options = new BridgeOptions()
                // No incoming messages permitted
                .addOutboundPermitted(tweetPermitted);

        CircuitBreaker registrationBreaker = CircuitBreaker.create("stream-registration", vertx);

        // Filter registrations and handle un-registrations
        sockJSHandler.bridge(options, be -> {
            if (be.type() == BridgeEventType.REGISTER) {
                String tag = getHashtag(be);
                registrationBreaker.execute(Future::complete);
                if (tag != null && twitterStreamHandler.trackTag(tag)) {
                    twitterStreamHandler.sendInitialTweetsFor(tag);
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
                        twitterStreamHandler.untrackTag(tag);
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
        if (address.toLowerCase().equals(address) && address.startsWith(TWEET_PREFIX)) {
            return address.substring(TWEET_PREFIX.length());
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
        messagePublish.execute(future -> {
            try {
                eventBus.publish(TWEET_PREFIX + tag, text);
                future.complete();
            } catch (Throwable t) {
                future.fail(t);
            }
        });
    }
}
