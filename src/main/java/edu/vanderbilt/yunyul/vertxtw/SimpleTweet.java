package edu.vanderbilt.yunyul.vertxtw;

import lombok.Data;
import twitter4j.Status;

@Data
public class SimpleTweet {
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
