package edu.vanderbilt.yunyul.vertxtw;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import lombok.Data;
import lombok.Setter;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static edu.vanderbilt.yunyul.vertxtw.TwitterWallVerticle.log;

public class TwitterStreamHandler {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern hashtag = Pattern.compile("^\\w+$");

    @Setter
    private TweetBroadcaster broadcaster;

    private final TwitterStream twitterStream;
    private final Set<String> trackedTags = new ConcurrentSkipListSet<>();
    // Streaming API already uses non Vert.x thread, no need to use one for updates either
    private final Executor filterUpdateThread = Executors.newSingleThreadExecutor();
    private final RateLimiter rateLimiter = RateLimiter.create(2);

    public TwitterStreamHandler(String consumerKey,
                                String consumerSecret,
                                String accessToken,
                                String accessTokenSecret) {
        log("Initializing Twitter handler...");

        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder()
                .setOAuthConsumerKey(consumerKey)
                .setOAuthConsumerSecret(consumerSecret)
                .setOAuthAccessToken(accessToken)
                .setOAuthAccessTokenSecret(accessTokenSecret)
                .setDebugEnabled(false);
        this.twitterStream = new TwitterStreamFactory(configurationBuilder.build()).getInstance();

        twitterStream.addListener(new StatusListener() {
            @Override
            public void onStatus(Status status) {
                try {
                    String statusJson = objectMapper.writeValueAsString(new SimpleTweet(status));
                    for (HashtagEntity e : status.getHashtagEntities()) {
                        broadcaster.broadcast(e.getText().toLowerCase(), statusJson);
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
    }

    private void updateFilters() {
        filterUpdateThread.execute(() -> {
            // Don't ever run out of tracked tags to prevent API errors, they won't broadcast anything
            if (!trackedTags.isEmpty()) {
                // Rate limit filter updates, blocks until permit acquired
                rateLimiter.acquire();
                // Blocking call, spins up new thread
                twitterStream.filter(trackedTags.toArray(new String[0]));
            }
        });
    }

    /**
     * Tracks the provided hashtag, if it matches the format requirements and isn't already tracked.
     *
     * @param tag Hashtag to track
     * @return Whether or not the tag was successfully tracked
     */
    public boolean trackTag(String tag) {
        if (tag.length() > 0 && tag.length() <= 30 && hashtag.matcher(tag).matches()) {
            if (trackedTags.add(tag.toLowerCase())) {
                log("Tracking " + tag);
                updateFilters();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Untracks the provided hashtag.
     *
     * @param tag Hashtag to untrack
     */
    public void untrackTag(String tag) {
        if (trackedTags.remove(tag.toLowerCase())) {
            log("Untracking " + tag);
            updateFilters();
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
