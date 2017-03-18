package edu.vanderbilt.yunyul.vertxtw;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Setter;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static edu.vanderbilt.yunyul.vertxtw.TwitterWallBootstrap.log;

public class TwitterHandler {
    @Setter
    private TweetBroadcaster broadcaster;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final TwitterStream twitterStream;
    private Set<String> trackedTags = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private boolean filterUpdateQueued = false;

    public TwitterHandler(String consumerKey, String consumerSecret, String accessToken, String accessTokenSecret) {
        log("Initializing Twitter handler...");
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder()
                .setOAuthConsumerKey(consumerKey)
                .setOAuthConsumerSecret(consumerSecret)
                .setOAuthAccessToken(accessToken)
                .setOAuthAccessTokenSecret(accessTokenSecret)
                .setDebugEnabled(false);
        twitterStream = new TwitterStreamFactory(configurationBuilder.build()).getInstance();

        twitterStream.addListener(new StatusListener() {
            @Override
            public void onStatus(Status status) {
                try {
                    String statusJson = objectMapper.writeValueAsString(new SimpleTweet(status));
                    for (HashtagEntity e : status.getHashtagEntities()) {
                        broadcaster.broadcast(e.getText(), statusJson);
                    }
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
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

        // Rate-limit filter updates
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            if (filterUpdateQueued) {
                int tags = trackedTags.size();
                if (tags > 0) {
                    twitterStream.filter(trackedTags.toArray(new String[tags]));
                }
                filterUpdateQueued = false;
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Tracks the provided hashtag, if it isn't already tracked
     *
     * @param tag Hashtag to track
     */
    public void trackTag(String tag) {
        if (tag.length() == 0) throw new IllegalArgumentException();
        if (trackedTags.add(tag.toLowerCase())) {
            filterUpdateQueued = true;
        }
    }

    /**
     * Untracks the provided hashtag
     *
     * @param tag Hashtag to untrack
     */
    public void untrackTag(String tag) {
        if (trackedTags.remove(tag.toLowerCase())) {
            filterUpdateQueued = true;
        }
    }

    @Data
    public static class SimpleTweet {
        private String text;
        private long time;
        private String username;
        private String userProfilePicture;
        private boolean isRetweet;
        private String originalUsername;
        // Javascript doesn't support 64-bit longs
        private String id;

        public SimpleTweet(Status status) {
            this.time = status.getCreatedAt().getTime();
            this.username = status.getUser().getScreenName();
            this.userProfilePicture = status.getUser().getMiniProfileImageURLHttps();
            this.isRetweet = status.isRetweet();
            if (this.isRetweet) {
                this.originalUsername = status.getRetweetedStatus().getUser().getScreenName();
                this.text = "RT @" + this.originalUsername + ": " + status.getRetweetedStatus().getText();
                this.id = Long.toString(status.getRetweetedStatus().getId());
            } else {
                this.text = status.getText();
                this.originalUsername = this.username;
                this.id = Long.toString(status.getId());
            }
        }
    }
}
