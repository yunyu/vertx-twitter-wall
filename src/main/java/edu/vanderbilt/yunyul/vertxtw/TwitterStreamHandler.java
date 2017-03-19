package edu.vanderbilt.yunyul.vertxtw;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import io.vertx.core.Handler;
import lombok.Data;
import lombok.Setter;
import twitter4j.*;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static edu.vanderbilt.yunyul.vertxtw.TwitterWallVerticle.log;

public class TwitterStreamHandler {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern hashtag = Pattern.compile("^\\w+$");

    @Setter
    private TweetBroadcaster broadcaster;
    private String[] lastTrackedTags;

    private final Twitter twitter;
    private final TwitterStream twitterStream;
    private final Set<String> trackedTags = new ConcurrentSkipListSet<>();

    // Streaming API already uses non Vert.x thread, no need to use one for updates either
    private final Executor filterUpdateThread = Executors.newSingleThreadExecutor();
    // Twitter does not make this information (stream disconnect/reconnect) information public
    // See https://dev.twitter.com/streaming/overview/connecting#rate-limiting
    // This value is an experimental guess
    private final RateLimiter filterUpdateRateLimiter = RateLimiter.create(0.25);
    private final Executor searchThread = Executors.newSingleThreadExecutor();
    // There's not too much point in making this configurable, as it's only used on registration
    // See https://dev.twitter.com/rest/reference/get/search/tweets
    private final RateLimiter searchRateLimiter = RateLimiter.create(0.7);

    static {
        System.setProperty("twitter4j.loggerFactory", "twitter4j.JULLoggerFactory");
    }

    public TwitterStreamHandler(String consumerKey,
                                String consumerSecret,
                                String accessToken,
                                String accessTokenSecret) {
        log("Initializing Twitter handler...");

        Configuration config = new ConfigurationBuilder()
                .setOAuthConsumerKey(consumerKey)
                .setOAuthConsumerSecret(consumerSecret)
                .setOAuthAccessToken(accessToken)
                .setOAuthAccessTokenSecret(accessTokenSecret)
                .setDebugEnabled(false)
                .build();
        this.twitter = new TwitterFactory(config).getInstance();
        this.twitterStream = new TwitterStreamFactory(config).getInstance();

        twitterStream.addListener(new StatusListener() {
            @Override
            public void onStatus(Status status) {
                String statusJson = safeToJsonString(new SimpleTweet(status));
                for (HashtagEntity e : status.getHashtagEntities()) {
                    broadcaster.broadcast(e.getText().toLowerCase(), statusJson);
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
            String[] tagArr = trackedTags.toArray(new String[0]);
            // Don't ever run out of tracked tags to prevent API errors, they won't broadcast anything
            if (tagArr.length > 0 && !Arrays.equals(tagArr, lastTrackedTags)) {
                lastTrackedTags = tagArr;
                // Rate limit filter updates, blocks until permit acquired
                filterUpdateRateLimiter.acquire();
                // Blocking call, spins up new thread
                twitterStream.filter(tagArr);
            }
        });
    }

    private boolean isTagValid(String tag) {
        return tag.length() > 0 && tag.length() <= 30 && hashtag.matcher(tag).matches();
    }

    private String safeToJsonString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException();
        }
    }

    /**
     * Tracks the provided hashtag, if it matches the format requirements and isn't already tracked.
     *
     * @param tag Hashtag to track
     * @return Whether or not the tag was successfully tracked
     */
    public boolean trackTag(String tag) {
        if (isTagValid(tag)) {
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

    /**
     * Performs a one-time search for tweets matching the provided hashtag
     *
     * @param tag      The hashtag to search for
     * @param callback The callback to call with the results as JSON strings
     */
    public void searchForTweets(String tag, Handler<String> callback) {
        if (isTagValid(tag)) {
            searchThread.execute(() -> {
                searchRateLimiter.acquire();
                try {
                    Query query = new Query("#" + tag);
                    query.setResultType(Query.RECENT);
                    query.count(30);
                    callback.handle(safeToJsonString(
                            twitter.search(new Query("#" + tag)).getTweets().stream()
                                    .map(SimpleTweet::new)
                                    .sorted(Comparator.comparingLong(SimpleTweet::getTime).reversed())
                                    .collect(Collectors.toList())
                    ));
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
            });
        } else {
            callback.handle(safeToJsonString(Collections.emptyList()));
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
        private String statusId;

        public SimpleTweet(Status status) {
            this.time = status.getCreatedAt().getTime();
            this.username = status.getUser().getScreenName();
            this.userProfilePicture = status.getUser().getMiniProfileImageURLHttps();
            this.isRetweet = status.isRetweet();
            this.statusId = Long.toString(status.getId());
            if (this.isRetweet) {
                this.originalUsername = status.getRetweetedStatus().getUser().getScreenName();
                this.text = "RT @" + this.originalUsername + ": " + status.getRetweetedStatus().getText();
                this.id = Long.toString(status.getRetweetedStatus().getId());
            } else {
                this.text = status.getText();
                this.originalUsername = this.username;
                this.id = this.statusId;
            }
        }
    }
}
