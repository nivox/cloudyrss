/*
 *  Copyright (c) 2011 Andrea Zito
 *
 *  This is free software; see lgpl-2.1.txt
 */
package cloudyrss;

import java.io.File;
import java.io.ObjectInputStream;
import java.net.URI;
import java.net.URISyntaxException;

import cloudypeer.PeerNode;
import cloudypeer.PeerSelector;
import cloudypeer.cloud.CloudURI;
import cloudypeer.cloud.StorageCloud;
import cloudypeer.epidemicbcast.antientropy.CloudEnabledAntiEntropyBroadcast;
import cloudypeer.epidemicbcast.rumormongering.RumorMongeringBroadcast;
import cloudypeer.peersampling.RandomPeerSelector;
import cloudypeer.peersampling.cloudcast.CloudCast;
import cloudypeer.store.Store;
import cloudypeer.store.StoreEntry;
import cloudypeer.store.StoreUpdateHandler;
import cloudypeer.store.diff.FakeDiffHandler;
import cloudypeer.store.persistence.BasicCloudPersistenceHandler;
import cloudypeer.store.persistence.InMemoryPersistenceHandler;
import cloudypeer.store.simple.SimpleStore;
import cloudypeer.store.simple.StoreEntryDiffHandler;
import de.nava.informa.core.ChannelExporterIF;
import de.nava.informa.core.ChannelIF;
import de.nava.informa.exporters.RSS_1_0_Exporter;
import de.nava.informa.impl.basic.ChannelBuilder;
import org.apache.log4j.Logger;
import java.util.Set;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import cloudypeer.network.NetworkHelper;
import java.net.InetAddress;
import cloudypeer.network.NetworkException;

/**
 * Cloudcast based feed reader
 *
 * @author Andrea Zito <zito.andrea@gmail.com>
 * @version 1.0
 */
public class CloudyFeedReader  implements StoreUpdateHandler {

  static Logger logger = Logger.getLogger(CloudyFeedReader.class);

  public static int PERSISTENCE = 5;

  public static int DEFAULT_PERIOD_ANTI_ENTROPY = 3600;
  public static int DEFAULT_PERIOD_RUMOR_MONGERING = 10;

  /* ***********************************************************
   * Instance variables
   *************************************************************/
  private NetworkHelper netHelper;
  private StorageCloud storageCloud;

  private Store localStore;
  private Store cloudStore;

  private CloudCast cloudCast;
  private CloudEnabledAntiEntropyBroadcast antiEntropy;
  private RumorMongeringBroadcast rumorMongering;

  private File rssFile;
  private String feedName;
  private String feedDescription;
  private String feedCloudProvider;
  private URI feedCloudURI;

  private volatile Date lastUpdate = null;
  private volatile boolean rssRefreshedSinceUpdate = false;

  private Set<CloudyFeedUpdateHandler> updateHandlers = new HashSet<CloudyFeedUpdateHandler>();

  private volatile boolean terminated = false;

  /* ***********************************************************
   * Constructor
   *************************************************************/

  public CloudyFeedReader(InetAddress ip, int basePort, File rssFile, String feedName, String feedDescription,
                          String cloudProvider, URI cloudURI)
    throws CloudyRSSException, IllegalArgumentException
  {
    this(ip, basePort, rssFile, feedName, feedDescription, cloudProvider, cloudURI, DEFAULT_PERIOD_ANTI_ENTROPY);
  }


  public CloudyFeedReader(InetAddress ip, int basePort, File rssFile, String feedName, String feedDescription,
                          String cloudProvider, URI cloudURI, int fetchPeriod)
    throws CloudyRSSException, IllegalArgumentException
  {
    CloudURI psCloudURI;
    CloudURI storeCloudURI;
    StoreEntryDiffHandler diffHandler;

    RandomPeerSelector antiEntropyPeerSelector;
    RandomPeerSelector rumorMongeringPeerSelector;

    if (feedName == null || feedName.trim().equals("")) {
      throw new IllegalArgumentException("Feed name cannot by empty");
    } else this.feedName = feedName;

    if (feedDescription == null) {
      this.feedDescription = "";
    } else this.feedDescription = feedDescription;

    this.rssFile = rssFile;
    this.feedCloudProvider = cloudProvider;
    this.feedCloudURI = cloudURI;

    try {
      try {
        /* Configuring Network Helper */
        int port = NetworkHelper.findFreePort(basePort, 0);
        if (port < 0) throw new NetworkException("Cannot find a free port");
        netHelper = NetworkHelper.getDefaultInstance(ip, port);
      } catch (NetworkException e) {
        throw new CloudyRSSException("Error configuring network helper", e);
      } catch (IOException e) {
        throw new CloudyRSSException("Input/Output error configuring network helper", e);
      }

      try {
        /* Configuring cloud URIs */
        psCloudURI = CloudURI.getInstance(cloudProvider,
                                          new URI(cloudURI.toString() + "/" + feedName + ".cloudcast_view"));
      } catch (URISyntaxException e) {
        throw new CloudyRSSException("Invalid peer sampler cloud URI", e);
      }
      try {
        storeCloudURI = CloudURI.getInstance(cloudProvider, new URI(cloudURI.toString()));
      } catch (URISyntaxException e) {
        throw new CloudyRSSException("Invalid cloud store URI", e);
      }

      /* Setup storage cloud*/
      this.storageCloud = StorageCloud.getInstance(cloudProvider, storeCloudURI);
    } catch (InstantiationException e) {
      throw new CloudyRSSException("Problem loading the specified cloud provider", e);
    }

    /* Setup the peer sampling */
    this.cloudCast = CloudCast.getDefaultInstance(netHelper.getLocalNode(), psCloudURI);

    /* Setup the store */
    diffHandler = new FakeDiffHandler();
    this.localStore = new SimpleStore(new InMemoryPersistenceHandler(), diffHandler);
    this.localStore.addUpdateHandler(this);

    this.cloudStore = new SimpleStore(new BasicCloudPersistenceHandler(storageCloud, (feedName + "/")), diffHandler);

    /* Setup the peer selectors */
    antiEntropyPeerSelector = new RandomPeerSelector(this.cloudCast);
    antiEntropyPeerSelector.getExcludedPeers().add(netHelper.getLocalNode());
    antiEntropyPeerSelector.excludeCloud(false);

    rumorMongeringPeerSelector = new RandomPeerSelector(this.cloudCast);
    rumorMongeringPeerSelector.getExcludedPeers().add(netHelper.getLocalNode());
    rumorMongeringPeerSelector.excludeCloud(true);

    /* Setup the epidemic broadcast protocols */
    this.antiEntropy = CloudEnabledAntiEntropyBroadcast.getDefaultInstance(netHelper.getLocalNode(),
                                                                           antiEntropyPeerSelector,
                                                                           localStore,
                                                                           cloudStore);

    this.rumorMongering = RumorMongeringBroadcast.getDefaultInstance(netHelper.getLocalNode(),
                                                                     rumorMongeringPeerSelector,
                                                                     localStore,
                                                                     PERSISTENCE);

    /* Setup the default epidemic broadcast periods */
    this.antiEntropy.setPeriod(fetchPeriod);
    this.antiEntropy.setProtocolData("nethelper", netHelper);
    this.rumorMongering.setPeriod(DEFAULT_PERIOD_RUMOR_MONGERING);
    this.rumorMongering.setProtocolData("nethelper", netHelper);
  }

  /* ***********************************************************
   * Update handlers management
   *************************************************************/

  public void addUpdateHandler(CloudyFeedUpdateHandler handler) {
    updateHandlers.add(handler);
  }

  public void removeUpdateHandler(CloudyFeedUpdateHandler handler) {
    updateHandlers.remove(handler);
  }

  /* ***********************************************************
   * Getters/Setters
   *************************************************************/

  public String getFeedName() {
    return feedName;
  }

  public String getFeedDescription() {
    return feedDescription;
  }

  public String getCloudProvider() {
    return feedCloudProvider;
  }

  public URI getCloudURI() {
    return feedCloudURI;
  }

  public String getFeedRssFileName() {
    return rssFile.getName();
  }

  public boolean hasRefreshedSinceUpdate() {
    return rssRefreshedSinceUpdate;
  }

  public Date getLastUpdateTimestamp() {
    return lastUpdate;
  }

  /* ***********************************************************
   * RSSEntry managements
   *************************************************************/

  public RSSEntry readRSSEntry(String key) {
    try {
      StoreEntry entry = localStore.getStoreEntry(key);
      ObjectInputStream in = new ObjectInputStream(entry.getInputStream());
      RSSEntry rssEntry = (RSSEntry) in.readObject();
      return rssEntry;
    } catch (Exception e) {
      logger.error("Error reading entry " + key, e);
    }
    return null;
  }

  private synchronized boolean generateRSS() {
    logger.info("Exporting feed " + feedName);
    try {
      ChannelBuilder builder = new ChannelBuilder();
      ChannelIF channel = builder.createChannel(feedName);
      channel.setDescription(feedDescription);

      String keys[] = localStore.listStoreEntries();

      for (String key: keys) {
        RSSEntry rssEntry = readRSSEntry(key);
        builder.createItem(channel, rssEntry.getTitle(), rssEntry.getDescription(), rssEntry.getLink());
      }

      ChannelExporterIF exporter = new RSS_1_0_Exporter(rssFile);

      exporter.write(channel);
      rssRefreshedSinceUpdate = true;
      return true;
    } catch (IOException e) {
      logger.error("Input/Output error exporting rss feed", e);
    } catch (RuntimeException e) {
      logger.error("Uncatched exception exporting rss feed", e);
    } finally {
      for (CloudyFeedUpdateHandler handler: updateHandlers) {
        handler.notifyUpdate(lastUpdate, this);
      }
    }

    return false;
  }

  public boolean forceUpdate() {
    return generateRSS();
  }

  public void notifyUpdate(String[] keys, Store store) {
    logger.info("Received keys update. Size: " + keys.length);
    lastUpdate = new Date();
    rssRefreshedSinceUpdate = false;

    generateRSS();
  }

  /* ***********************************************************
   * Thread managements
   *************************************************************/
  public void start() throws CloudyRSSException {
    this.cloudCast.start();
    this.antiEntropy.start();
    this.rumorMongering.start();
  }

  public void terminate() throws CloudyRSSException {
    this.cloudCast.terminate();
    this.antiEntropy.terminate();
    this.rumorMongering.terminate();
    this.terminated = true;
    logger.info("Terminating feed " + feedName);
  }
}
