Vert.x Twitter Wall
==

![](https://i.imgur.com/sZqt0CO.png)
**[Live demo](http://twitterwall.yunyul.in/)**

Twitter Wall project for Vert.x. Streams tweets using Twitter4J through the SockJS event bus bridge. Uses mini.css and vue.js on the frontend.

Uses the Twitter search API to display initial tweets and upgrades to the streaming API when available.

Usage
--

1. Build using `mvn clean package`
2. Move the JAR in `/target` to your preferred location
3. Fill in `config.json` (see Configuration section and `config.example.json`)
4. Move `config.json` to same directory as the JAR
5. Run `java -jar vertx-twitter-wall-1.0-SNAPSHOT.jar -conf config.json`
6. Visit `localhost:<port in config>`

Configuration
--

* The tokens and secrets can be obtained through these [instructions](http://stackoverflow.com/a/12335636).
* `filterUpdateRateLimit` is how many max stream reconnections to allow per second. Twitter does not document the rate limit for this. Don't change this without good reason.
* `initialTweetRateLimit` is how many lookups of initial tweets to allow per second. This is done using the user bucket. See https://dev.twitter.com/rest/public/rate-limits.
* `searchPeriodInSeconds` controls the period for the fallback search-based lookup using the app bucket. See https://dev.twitter.com/rest/public/rate-limits.

TODO
--

* Implement multiple hashtag tracking on the frontend
