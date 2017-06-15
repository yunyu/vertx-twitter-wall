package edu.vanderbilt.yunyul.vertxtw;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.RateLimiter;
import io.vertx.core.json.JsonObject;
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

    // Streaming
    private final TwitterStream twitterStream;
    // Polling
    private final Twitter twitterAppAuth;
    // Initial tweets
    private final Twitter twitterUserAuth;
    private final Set<String> trackedTags = new ConcurrentSkipListSet<>();
    private final Deque<String> tagQueue = new ConcurrentLinkedDeque<>();

    // Streaming API already uses non Vert.x thread, no need to use one for updates either
    private final ScheduledExecutorService filterUpdateThread = Executors.newSingleThreadScheduledExecutor();
    private final RateLimiter filterUpdateRateLimiter;
    private final ScheduledExecutorService initialTweetThread = Executors.newSingleThreadScheduledExecutor();
    private final RateLimiter initialTweetRateLimiter;

    static {
        System.setProperty("twitter4j.loggerFactory", "twitter4j.JULLoggerFactory");
    }

    public TwitterStreamHandler(JsonObject vertxConfig) {
        log("Initializing Twitter handler...");

        // Twitter does not make this rate limit (stream disconnect/reconnect) information public
        // See https://dev.twitter.com/streaming/overview/connecting#rate-limiting
        // This value is an experimental guess
        this.filterUpdateRateLimiter = RateLimiter.create(
                vertxConfig.getDouble("filterUpdateRateLimit", 0.3)
        );
        this.initialTweetRateLimiter = RateLimiter.create(
                vertxConfig.getDouble("initialTweetRateLimit", 0.2)
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
        this.twitterAppAuth = new TwitterFactory(appConfig).getInstance();
        try {
            twitterAppAuth.getOAuth2Token(); // This is required to use app auth
        } catch (TwitterException e) {
            e.printStackTrace();
        }
        this.twitterUserAuth = new TwitterFactory(config).getInstance();

        twitterStream.addListener(new StatusListener() {
            @Override
            public void onStatus(Status status) {
                SimpleTweet st = new SimpleTweet(status);
                for (HashtagEntity e : status.getHashtagEntities()) {
                    log("Broadcasting tweet " + st.getStatusId() + " by " + st.getUsername());
                    broadcaster.broadcast(e.getText().toLowerCase(), safeToJsonString(st));
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

        // Fall back to search API if connection lost
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                    if (streamConnected.get()) {
                        return;
                    }
                    List<String> tagsToSearch = new ArrayList<>();
                    if (!tagQueue.isEmpty()) {
                        log("Tracking tag queue: " + tagQueue.toString());
                        // "Rotate" the elements in the queue to give everything a chance
                        // More tweets will be returned by more active hashtags, possibly "crowding out" the others
                        // Susceptible to denial of service if rate of adding new hashtags is too high,
                        // but you're supposed to use Firehose for that situation
                        int tagQueueSize = tagQueue.size();
                        // Twitter caps to 10 max keywords
                        if (tagQueueSize > 10) {
                            for (int i = 0; i < 10; i++) {
                                String el = tagQueue.removeFirst();
                                tagsToSearch.add(el);
                                tagQueue.addLast(el);
                            }
                        } else {
                            if (tagQueueSize > 1) {
                                tagQueue.addLast(tagQueue.removeFirst());
                            }
                            tagsToSearch.addAll(tagQueue);
                        }
                    }
                    if (tagsToSearch.isEmpty()) {
                        return;
                    }
                    String queryString = orJoiner.join(tagsToSearch.stream()
                            .map(el -> "#" + el).collect(Collectors.toList()));
                    try {
                        List<Status> statuses = getTweetsForQuery(twitterAppAuth, queryString);
                        for (String tag : tagsToSearch) {
                            // I could probably optimize the algorithm a bit more, but this isn't a bottleneck
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
                }, 0,
                (long) (vertxConfig.getDouble("searchPeriodInSeconds", 2.0D) * 1000),
                TimeUnit.MILLISECONDS);
    }

    private List<Status> getTweetsForQuery(Twitter twitter, String queryString) throws TwitterException {
        Query query = new Query(queryString);
        query.setResultType(Query.RECENT);
        query.count(100);
        return twitter.search(query).getTweets();
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
                log("Stream disconnected");
                streamConnected.set(false);
                // Blocking call, spins up new thread
                twitterStream.filter(tagArr);
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
        initialTweetThread.execute(() -> {
            initialTweetRateLimiter.acquire();
            try {
                broadcaster.broadcast(tag,
                        safeToJsonString(getTweetsForQuery(twitterUserAuth, "#" + tag).stream()
                                .map(SimpleTweet::new).collect(Collectors.toList())
                        ));
            } catch (TwitterException e) {
                e.printStackTrace();
            }
        });
    }

}
