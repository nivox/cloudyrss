/*
 *  Copyright (c) 2011 Andrea Zito
 *
 *  This is free software; see lgpl-2.1.txt
 */
package cloudyrss;

import org.apache.log4j.Logger;
import cloudypeer.network.NetworkHelper;
import java.net.URI;
import java.net.InetAddress;
import java.util.Properties;
import java.io.File;
import java.io.FileInputStream;
import cloudyrss.CloudyFeedReader;
import java.util.HashMap;
import org.jibble.simplewebserver.SimpleWebServer;
import java.io.IOException;
import java.io.FileOutputStream;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Main class for CloudyRSS.
 *
 * @author Andrea Zito <zito.andrea@gmail.com>
 * @version 1.0
 */
public class CloudyRSS {

  static Logger logger = Logger.getLogger(CloudyRSS.class);

  /* ***********************************************************
   *  Instance variables
   *************************************************************/

  private SimpleWebServer httpServer;
  private File baseDir;
  private File feedsConfFile;

  private InetAddress ip;
  private int port;
  private int httpServerPort;
  private int fetchPeriod;

  private HashMap<String, CloudyFeedReader> feedMap = new HashMap<String, CloudyFeedReader>();
  private HashMap<String, File> rssFileMap = new HashMap<String, File>();

  /* ***********************************************************
   *  Constructor
   *************************************************************/
  public CloudyRSS(Properties conf, File feedsConf) throws Exception
  {
    this.feedsConfFile = feedsConf;

    String confip = conf.getProperty("ip");
    if (confip == null) {
      logger.warn("IP address configuration missing, using loopback");
    }
    this.ip = InetAddress.getByName(confip);

    String confport = conf.getProperty("port");
    if (confport == null) {
      logger.warn("Base port number missing, using default");
      this.port = 20000;
    } else {
      this.port = Integer.parseInt(confport);
    }

    String confperiod = conf.getProperty("period");
    if (confport == null) {
      logger.warn("Base port number missing, using default");
      this.fetchPeriod = CloudyFeedReader.DEFAULT_PERIOD_ANTI_ENTROPY;
    } else {
      this.fetchPeriod = Integer.parseInt(confperiod);
    }

    String confhttpport = conf.getProperty("httpServerPort");
    if (confhttpport == null) {
      logger.error("Missing configuration parameter 'htppServerPort', using default: 1234");
      this.httpServerPort = 1234;
    } else {
      this.httpServerPort = Integer.parseInt(confhttpport);
    }

    this.baseDir = new File(conf.getProperty("basedir", ""));
    if (!baseDir.isDirectory())
      throw new IllegalArgumentException("Configured base directory not found!");

    httpServer = new SimpleWebServer(baseDir, httpServerPort);
  }

  private CloudyFeedReader configureNewFeed(String name, String description, String cloudProvider,
                                  URI cloudURI) throws Exception
  {
    CloudyFeedReader feedReader;
    File rssFile;
    try {
      logger.info("Configuring feed " + name);
      rssFile = new File(baseDir.getPath() + File.separator + name + ".rss");
      feedReader= new CloudyFeedReader(ip, port, rssFile , name, description, cloudProvider,
                                       cloudURI);
      feedReader.start();
      feedReader.forceUpdate();
      feedMap.put(name, feedReader);
      rssFileMap.put(name, rssFile);
      return feedReader;
    } catch (IllegalArgumentException e) {
      logger.error("Error in feed configuration", e);
      return null;
    }
  }

  /* ***********************************************************
   *  Public methods
   *************************************************************/
  public void loadFeedsConfiguration() throws RuntimeException {
    Properties conf =  new Properties();
    try {
      conf.load(new FileInputStream(feedsConfFile));
    } catch (IOException e) {
      throw new RuntimeException("Error loading feeds configuration", e);
    }

    String feeds[] = conf.getProperty("feeds", "").split(";");

    for (String feed: feeds) {
      if (feed.trim().equals("")) continue;
      String description = conf.getProperty(feed + ".description", null);
      if (description == null) {
        logger.warn("Missing description for feed " + feed);
        continue;
      }

      String cloudProvider = conf.getProperty(feed + ".cloudProvider", null);
      if (cloudProvider == null) {
        logger.warn("Missing cloud provider for feed " + cloudProvider);
        continue;
      }

      String cloudURIString = conf.getProperty(feed + ".cloudURI", null);
      if (cloudProvider == null) {
        logger.warn("Missing cloud URI for feed " + cloudURIString);
        continue;
      }

      URI cloudURI;
      try {
        cloudURI = new URI(cloudURIString);
      } catch (URISyntaxException e) {
        throw new RuntimeException("Cloud URI not valid for feed " + feed);
      }

      try {
        configureNewFeed(feed, description, cloudProvider, cloudURI);
      } catch (Exception e) {
        throw new RuntimeException("Error configuring feed " + feed, e);
      }
    }
  }

  public void writeFeedsConfiguration() throws RuntimeException {
    Properties prop = new Properties();
    String feeds = null;
    for (String feed: getConfiguredFeeds()) {
      if (feeds == null) feeds = feed;
      else feeds += ";" + feed;

      CloudyFeedReader reader = getCloudyFeedReaderForFeed(feed);
      String description = reader.getFeedDescription();
      String cloudProvider = reader.getCloudProvider();
      String cloudURI = reader.getCloudURI().toString();

      prop.setProperty(feed + ".description", description);
      prop.setProperty(feed + ".cloudProvider", cloudProvider);
      prop.setProperty(feed + ".cloudURI", cloudURI);
    }
    if (feeds == null) feeds = "";
    prop.setProperty("feeds", feeds);
    try {
      prop.store(new FileOutputStream(feedsConfFile), "Automatically generated by CloudyRSS. Don't edit manually");
    } catch (IOException e) {
      throw new RuntimeException("Error saving feeds configuration", e);
    }
  }

  public CloudyFeedReader configureFeed(URL cloudyRssFileURL) throws RuntimeException {
    Properties feedConf = new Properties();
    try {
      feedConf.load(cloudyRssFileURL.openStream());
    } catch (IOException e) {
      throw new RuntimeException("Error reading feed configuration file");
    }

    String feedName = feedConf.getProperty("feed", null);
    String feedDescription = feedConf.getProperty("description", null);
    String cloudProvider = feedConf.getProperty("cloudProvider", null);
    String cloudURI = feedConf.getProperty("cloudURI", null);

    try {
      return configureNewFeed(feedName, feedDescription, cloudProvider, new URI(cloudURI));
    } catch (Exception e) {
      throw new RuntimeException("Error configuring feed");
    }
  }

  public boolean removeFeed(String name) throws RuntimeException {
    CloudyFeedReader toRemove = feedMap.get(name);
    if (toRemove == null) return false;

    toRemove.terminate();
    feedMap.remove(name);
    File rss = rssFileMap.remove(name);
    System.out.println(rss.delete());
    return true;
  }

  public String[] getConfiguredFeeds() {
    return feedMap.keySet().toArray(new String[feedMap.size()]);
  }

  public CloudyFeedReader getCloudyFeedReaderForFeed(String feed) {
    return feedMap.get(feed);
  }

  public File getRSSFileForFeed(String feed) {
    return rssFileMap.get(feed);
  }

  public String getWebServerURL() {
    return new String("http://localhost:" + httpServerPort);
  }

  /* ***********************************************************
   *  Main
   *************************************************************/
  public static void main(String args[]) {
    Properties conf = new Properties();
    String confFilePath = "conf.properties";
    String feedsConfFilePath = "feeds.properties";

    if (args.length > 0) confFilePath = args[0];
    if (args.length > 1) feedsConfFilePath = args[1];

    try {
      conf.load(new FileInputStream(confFilePath));
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }


    try {
      CloudyRSS cloudyRSS = new CloudyRSS(conf, new File(feedsConfFilePath));
      cloudyRSS.loadFeedsConfiguration();
      Thread.currentThread().join();
    } catch (Exception e) {
      logger.error("Main app exception", e);
    }
  }
}
