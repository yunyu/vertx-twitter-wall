package edu.vanderbilt.yunyul.vertxtw;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.RateLimiter;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.Setter;
import twitter4j.*;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static edu.vanderbilt.yunyul.vertxtw.TwitterWallVerticle.log;

public class TwitterStreamHandler {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern hashtag = Pattern.compile("^\\w+$");
    private static final Joiner orJoiner = Joiner.on(" OR ");

    @Setter
    private TweetBroadcaster broadcaster;
    private String[] lastTrackedTags = new String[0];
    private AtomicBoolean filterUpdateQueued = new AtomicBoolean(false);
    private AtomicBoolean streamConnected = new AtomicBoolean(false);

    private final Twitter twitter;
    private final TwitterStream twitterStream;
    private final Set<String> trackedTags = new ConcurrentSkipListSet<>();
    private final Deque<String> tagQueue = new ConcurrentLinkedDeque<>();
    private final Deque<String> initialTweetsQueue = new ConcurrentLinkedDeque<>();

    // Streaming API already uses non Vert.x thread, no need to use one for updates either
    private final ScheduledExecutorService filterUpdateThread = Executors.newSingleThreadScheduledExecutor();
    private final RateLimiter filterUpdateRateLimiter;

    static {
        System.setProperty("twitter4j.loggerFactory", "twitter4j.JULLoggerFactory");
    }

    public TwitterStreamHandler(JsonObject vertxConfig) {
        log("Initializing Twitter handler...");

        // Twitter does not make this information (stream disconnect/reconnect) information public
        // See https://dev.twitter.com/streaming/overview/connecting#rate-limiting
        // This value is an experimental guess
        this.filterUpdateRateLimiter = RateLimiter.create(
                vertxConfig.getDouble("filterUpdateRateLimit", 0.3)
        );

        String consumerKey = vertxConfig.getString("consumerKey");
        String consumerSecret = vertxConfig.getString("consumerSecret");
        String accessToken = vertxConfig.getString("accessToken");
        String accessTokenSecret = vertxConfig.getString("accessTokenSecret");

        Configuration config = new ConfigurationBuilder()
                .setOAuthConsumerKey(consumerKey)
                .setOAuthConsumerSecret(consumerSecret)
                .setOAuthAccessToken(accessToken)
                .setOAuthAccessTokenSecret(accessTokenSecret)
                .setDebugEnabled(false)
                .build();
        this.twitterStream = new TwitterStreamFactory(config).getInstance();

        Configuration appConfig = new ConfigurationBuilder()
                .setOAuthConsumerKey(consumerKey)
                .setOAuthConsumerSecret(consumerSecret)
                .setDebugEnabled(false)
                .setApplicationOnlyAuthEnabled(true)
                .build();
        this.twitter = new TwitterFactory(appConfig).getInstance();
        try {
            twitter.getOAuth2Token();
        } catch (TwitterException e) {
            e.printStackTrace();
        }

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

        twitterStream.addConnectionLifeCycleListener(new ConnectionLifeCycleListener() {
            @Override
            public void onConnect() {
                streamConnected.set(true);
                tagQueue.clear();
                tagQueue.addAll(trackedTags);
                log("Registered stream connection");
            }

            @Override
            public void onDisconnect() {

            }

            @Override
            public void onCleanUp() {

            }
        });

        // Handle initial tweet loads, also fall back to search API if connection hang
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                Deque<String> currQueue;
                boolean rotateElements;
                if (!initialTweetsQueue.isEmpty()) {
                    currQueue = initialTweetsQueue;
                    log("Tracking initial tweets: " + currQueue.toString());
                    rotateElements = false;
                } else if (!streamConnected.get()) {
                    currQueue = tagQueue;
                    log("Tracking tag queue: " + currQueue.toString());
                    rotateElements = true;
                } else {
                    return;
                }
                List<String> tagsToSearch = new ArrayList<>();
                if (!currQueue.isEmpty()) {
                    // If over 10 elements, "rotate" the elements in the queue
                    // This is susceptible to a small DoS if elements are constantly added
                    // But this is not a serious project
                    if (currQueue.size() > 10) {
                        for (int i = 0; i < 10; i++) {
                            String el = currQueue.removeFirst();
                            tagsToSearch.add(el);
                            if (rotateElements) {
                                currQueue.addLast(el);
                            }
                        }
                    } else {
                        tagsToSearch.addAll(currQueue);
                        if (!rotateElements) {
                            currQueue.clear();
                        }
                    }
                }
                if (tagsToSearch.isEmpty()) {
                    return;
                }
                String queryString = orJoiner.join(tagsToSearch.stream()
                        .map(el -> "#" + el).collect(Collectors.toList()));
                try {
                    Query query = new Query(queryString);
                    query.setResultType(Query.RECENT);
                    query.count(100);
                    List<Status> statuses = twitter.search(query).getTweets();
                    for (String tag : tagsToSearch) {
                        List<SimpleTweet> tweetsMatchingTag = statuses.stream()
                                .filter(status -> {
                                    for (HashtagEntity e : status.getHashtagEntities()) {
                                        if (e.getText().equalsIgnoreCase(tag)) {
                                            return true;
                                        }
                                    }
                                    return false;
                                }).map(SimpleTweet::new).collect(Collectors.toList());
                        broadcaster.broadcast(tag.toLowerCase(), safeToJsonString(tweetsMatchingTag));
                    }
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
        }, 0, (long) (vertxConfig.getDouble("searchPeriodInSeconds", 2.0D) * 1000), TimeUnit.MILLISECONDS);
    }

    private void updateFilters() {
        // compareAndSet returns false if actual != expect
        if (!filterUpdateQueued.compareAndSet(false, true)) {
            return;
        }
        filterUpdateThread.schedule(() -> {
            // Rate limit filter updates, blocks until permit acquired
            filterUpdateRateLimiter.acquire();
            filterUpdateQueued.set(false);
            String[] tagArr = trackedTags.toArray(new String[0]);
            // Don't ever run out of tracked tags to prevent API errors, they won't broadcast anything
            // Similarly, don't update filters if tagArr is a subset
            if (tagArr.length > 0 && !Arrays.asList(lastTrackedTags).containsAll(Arrays.asList(tagArr))) {
                lastTrackedTags = tagArr;
                log("Filtering to " + Arrays.toString(tagArr));
                // Blocking call, spins up new thread
                twitterStream.filter(tagArr);
                log("Stream disconnected");
                streamConnected.set(false);
            }
        }, 500, TimeUnit.MILLISECONDS); // "Bunch up" requests within 500ms
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
            tag = tag.toLowerCase();
            if (trackedTags.add(tag)) {
                log("Tracking " + tag);
                tagQueue.addFirst(tag);
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
        tag = tag.toLowerCase();
        if (trackedTags.remove(tag)) {
            log("Untracking " + tag);
            tagQueue.remove(tag);
            updateFilters();
        }
    }

    /**
     * Performs a one-time search for tweets matching the provided hashtag
     *
     * @param tag The hashtag to search for
     */
    public void sendInitialTweetsFor(String tag) {
        log("Adding to initial tweets queue: " + tag);
        initialTweetsQueue.add(tag);
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
