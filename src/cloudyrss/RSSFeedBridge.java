/*
 *  Copyright (c) 2011 Andrea Zito
 *
 *  This is free software; see lgpl-2.1.txt
 */
package cloudyrss;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import cloudypeer.cloud.CloudException;
import cloudypeer.cloud.CloudURI;
import cloudypeer.cloud.StorageCloud;
import cloudyrss.RSSEntry;
import de.nava.informa.core.ChannelIF;
import de.nava.informa.core.ItemIF;
import de.nava.informa.core.ParseException;
import de.nava.informa.impl.basic.ChannelBuilder;
import de.nava.informa.parsers.FeedParser;
import de.nava.informa.utils.poller.Poller;
import de.nava.informa.utils.poller.PollerApproverIF;
import de.nava.informa.utils.poller.PollerObserverIF;
import org.apache.log4j.Logger;
import java.util.Date;
import de.nava.informa.utils.ItemComparator;
import java.util.Arrays;


/**
 * Simple app that monitors rss fedds and reflect their changes to the cloud.
 *
 * @author Andrea Zito <zito.andrea@gmail.com>
 * @version 1.0
 */
public class RSSFeedBridge {

  static Logger logger = Logger.getLogger(RSSFeedBridge.class);

  /* ***********************************************************
   *  Instance variables
   *************************************************************/
  private HashMap<ChannelIF, StorageCloud> feedCloudMap = new HashMap<ChannelIF, StorageCloud>();
  private HashMap<ChannelIF, String> feedNameMap = new HashMap<ChannelIF, String>();
  private HashMap<ChannelIF, List<RSSEntry>> entryCache = new HashMap<ChannelIF, List<RSSEntry>>();
  private HashMap<ChannelIF, Date> feedThresholdMap = new HashMap<ChannelIF, Date>();
  private Poller poller = new Poller();

  /* ***********************************************************
   *  Observer class implementation
   *************************************************************/
  private PollerObserverIF observer = new PollerObserverIF() {
      public void itemFound(ItemIF item, ChannelIF channel) {
        logger.trace("Update for feed " + channel);
        channel.addItem(item);
        RSSEntry e = new RSSEntry(item.getTitle(), item.getDescription(), item.getLink(), item.getDate());
        synchronized (entryCache) {
          List<RSSEntry> cache = entryCache.get(channel);
          if (cache == null) {
            cache = new ArrayList<RSSEntry>();
            entryCache.put(channel, cache);
          }

          cache.add(e);
          entryCache.notify();
          channel.setLastUpdated(new Date());
        }
      }

      public void channelErrored(ChannelIF channel, Exception e) {
        e.printStackTrace();
      }

      public void channelChanged(ChannelIF channel) {}

      public void pollStarted(ChannelIF channel) {}

      public void pollFinished(ChannelIF channel) {}
    };

  /* ***********************************************************
   *  Observer class implementation
   *************************************************************/
  PollerApproverIF approver = new PollerApproverIF() {
      public boolean canAddItem(ItemIF item, ChannelIF channel) {
        Date threshold = feedThresholdMap.get(channel);
        if (threshold == null) return true;

        return threshold.getTime() < item.getDate().getTime();
      }
    };

  /* ***********************************************************
   *  Constructors
   *************************************************************/
  public RSSFeedBridge(Properties conf) {
    String feedURLStrings[];
    URL feedURL;
    CloudURI cloudURI;
    ChannelIF channel;
    StorageCloud storageCloud;
    String cloudProvider;
    String cloudURIString;
    String components[];

    this.poller = new Poller();
    this.poller.addObserver(observer);
    this.poller.addApprover(approver);
    String feeds[] = conf.getProperty("feeds", "").split(";");
    int period = Integer.parseInt(conf.getProperty("fetch-period")) * 1000;
    this.poller.setPeriod(period);
    for (String feed: feeds) {
      logger.info("Configuring feed " + feed);
      try {
        feedURLStrings = conf.getProperty(feed + ".feedurls", "").split(";");
        if (feedURLStrings.length == 0) {
          logger.warn("No feed configured for " + feed);
        }
        cloudProvider = conf.getProperty(feed + ".cloudProvider", "");
        cloudURIString = conf.getProperty(feed + ".cloudURI", "");

        cloudURI = CloudURI.getInstance(cloudProvider, new URI(cloudURIString));
        storageCloud = StorageCloud.getInstance(cloudProvider, cloudURI);

        for (String feedURLString: feedURLStrings) {
          if (feedURLString.trim().equals("")) continue;
          logger.info("Adding rss feed " + feedURLString);
          channel = FeedParser.parse(new ChannelBuilder(), new URL(feedURLString));
          feedCloudMap.put(channel, storageCloud);
          feedNameMap.put(channel, feed);
          feedThresholdMap.put(channel, null);
          poller.registerChannel(channel);
        }
      } catch (MalformedURLException e) {
        logger.error(String.format("Ignoring feed %s as it's not a valid URL", feed));
      } catch (URISyntaxException e) {
        logger.error(String.format("Ignoring feed %s as the associated cloud URI is not a valid", feed));
      } catch (ArrayIndexOutOfBoundsException e) {
        logger.error(String.format("Cloud URI configuration error for fee %s. Ignoring", feed));
      } catch (InstantiationException e) {
        logger.error(String.format("Error instantiating storage cloud for feed %s", feed));
      } catch (IOException e) {
        logger.error(String.format("I/O error parsing feed %s", feed));
      } catch (ParseException e) {
        logger.error(String.format("Error parsing feed %s", feed));
      }
    }
  }

  public void run() {
    Random r = new Random();
    List<RSSEntry> entries = null;
    ChannelIF channel = null;
    while(true) {
      synchronized (entryCache) {
        try {
          if (entryCache.size() ==  0) {
            logger.trace("Waiting for news");
            entryCache.wait();
          }
        } catch (InterruptedException e) {}

        channel = null;
        for (ChannelIF c: entryCache.keySet()) {
          channel = c;
          break;
        }

        if (channel != null) {
          logger.trace("Found a channel with updates: " + channel);
          entries = entryCache.remove(channel);
        } else {
          logger.trace("No channel with updates");
        }
      }

      ItemIF[] items = channel.getItems().toArray(new ItemIF[] {});
      java.util.Arrays.sort(items, new ItemComparator(true));
      int count = 0;
      Date newThreshold = null;
      for (ItemIF item: items) {
        count++;
        if (count > 5) {
          if (newThreshold == null) newThreshold = item.getDate();
          channel.removeItem(item);
        }
      }
      if (newThreshold != null)
        feedThresholdMap.put(channel, newThreshold);

      if (entries != null) {
        StorageCloud cloud = feedCloudMap.get(channel);
        String baseKey = feedNameMap.get(channel);
        ByteArrayOutputStream out;
        ObjectOutputStream objOut;
        ByteArrayInputStream in;

        logger.info(String.format("Uploading %s updates for feed %s", entries.size(), baseKey));

        for (RSSEntry e: entries) {
          if (e == null) continue;
          try {
            out = new ByteArrayOutputStream();
            objOut = new ObjectOutputStream(out);
            objOut.writeObject(e);
            objOut.close();

            in = new ByteArrayInputStream(out.toByteArray());
            cloud.put(baseKey + "/" + e.getKey(), "test.simple.RSSEntry", in, null);
          } catch (IOException ex) {
            logger.error("I/O Error putting on cloud entry" + e, ex);
          } catch (CloudException ex) {
            logger.error("Cloud error putting on cloud entry" + e, ex);
          }
        }
        channel = null;
        entries = null;
      }
    }
  }

  /* ***********************************************************
   *  Main
   *************************************************************/

  public static void main(String args[]) {
    if (args.length < 1) {
      System.err.println("Missing command line parameter: feed properties file path");
      System.exit(1);
    }

    FileInputStream confFileInputStream = null;

    try {
      confFileInputStream = new FileInputStream(args[0]);
    } catch (FileNotFoundException e) {
      System.err.println("Configuration file not found: " + args[0]);
      System.exit(1);
    }

    Properties conf = new Properties();
    try {
      conf.load(confFileInputStream);
    } catch (IOException e) {
      System.err.println("Input/Output error parsing configuration file: " + e.getMessage());
      System.exit(1);
    }

    RSSFeedBridge RSSFeedBridge = new RSSFeedBridge(conf);
    RSSFeedBridge.run();
  }
}
