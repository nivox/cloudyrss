/*
 *  Copyright (c) 2011 Andrea Zito
 *
 *  This is free software; see lgpl-2.1.txt
 */
package cloudyrss;

import java.io.Serializable;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Representation of an RSS entry.
 *
 * @author Andrea Zito <zito.andrea@gmail.com>
 * @version 1.0
 */
public class RSSEntry implements Serializable {
  private static  DateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

  private String title;
  private String description;
  private URL link;
  private Date publicationDate;

  public RSSEntry(String title, String description, URL link, Date publicationDate) {
    this.title = title;
    this.description = description;
    this.link = link;
    this.publicationDate = publicationDate;
  }

  public String getKey() {
    return String.format("%s/%s", df.format(publicationDate), title.hashCode());
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public URL getLink() {
    return link;
  }

  public Date publicationDate() {
    return publicationDate;
  }

  public String toString() {
    return getKey();
  }
}
