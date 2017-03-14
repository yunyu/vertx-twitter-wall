Vert.x Twitter Wall
==

![](https://i.imgur.com/qqZmDqJ.png)
**[Live demo](http://twitterwall.yunyul.in/)**

Twitter Wall project for Vert.x. Streams tweets using Twitter4J through SockJS. Uses mini.css and vue.js on the frontend.

Usage
--

1. Build using `mvn clean package`, and move the JAR in '/target' to your preferred location
2. Put credentials in `config.properties` (see `config.example.properties`), and put it in the same directory as the JAR
3. `java -jar vertx-twitter-wall-1.0-SNAPSHOT.jar`
4. Application is running on `localhost:8080`