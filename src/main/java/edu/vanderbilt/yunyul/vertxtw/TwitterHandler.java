package edu.vanderbilt.yunyul.vertxtw;

import lombok.Setter;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static edu.vanderbilt.yunyul.vertxtw.TwitterWallBootstrap.log;

/**
 * Class that sets up the Twitter4J Streaming API and broadcasts it to the TweetBroadcaster
 */
public class TwitterHandler {
    @Setter
    private TweetBroadcaster broadcaster;

    private TwitterStream twitterStream;
    private Set<String> trackedTags = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    public TwitterHandler(String consumerKey, String consumerSecret, String accessToken, String accessTokenSecret) {
        log("Initializing Twitter handler...");
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder()
                .setOAuthConsumerKey(consumerKey)
                .setOAuthConsumerSecret(consumerSecret)
                .setOAuthAccessToken(accessToken)
                .setOAuthAccessTokenSecret(accessTokenSecret);
        twitterStream = new TwitterStreamFactory(configurationBuilder.build()).getInstance();

        twitterStream.addListener(new StatusListener() {
            @Override
            public void onStatus(Status status) {
                for (HashtagEntity e : status.getHashtagEntities()) {
                    broadcaster.broadcast(e.getText(), status.getText());
                }
            }

            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {

            }

            @Override
            public void onTrackLimitationNotice(int i) {

            }

            @Override
            public void onScrubGeo(long l, long l1) {

            }

            @Override
            public void onStallWarning(StallWarning stallWarning) {

            }

            @Override
            public void onException(Exception e) {

            }
        });
    }

    private void updateFilters() {
        int tags = trackedTags.size();
        if (tags > 0) {
            twitterStream.filter(trackedTags.toArray(new String[tags]));
        }
    }

    /**
     * Tracks the provided hashtag, if it isn't already tracked
     * @param tag Hashtag to track
     */
    public void trackTag(String tag) {
        if (tag.length() == 0) throw new IllegalArgumentException();
        if (trackedTags.add(tag)) {
            updateFilters();
        }
    }

    /**
     * Untracks the provided hashtag
     * @param tag Hashtag to untrack
     */
    public void untrackTag(String tag) {
        if (trackedTags.remove(tag)) {
            updateFilters();
        }
    }
}
