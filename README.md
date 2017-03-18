Vert.x Twitter Wall
==

![](https://i.imgur.com/sZqt0CO.png)
**[Live demo](http://twitterwall.yunyul.in/)**

Twitter Wall project for Vert.x. Streams tweets using Twitter4J through SockJS. Uses mini.css and vue.js on the frontend.

Usage
--

1. Build using `mvn clean package`
2. Move the JAR in `/target` to your preferred location
3. Acquire credentials, see [instructions](http://stackoverflow.com/a/12335636)
4. Put credentials and bind port in `config.properties` (see `config.example.properties`)
5. Move `config.properties` to same directory as the JAR
6. Run `java -jar vertx-twitter-wall-1.0-SNAPSHOT.jar`
7. Visit `localhost:<port in config>`

TODO
--

* Implement multiple hashtag tracking on the frontend
* Display old tweets initially (?)