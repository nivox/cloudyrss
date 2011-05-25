/*
 *  Copyright (c) 2011 Andrea Zito
 *
 *  This is free software; see lgpl-2.1.txt
 */
package cloudyrss;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;


/**
 * Shutdown hook whichsave the configuration of CloudyRSS
 */
class SavePreferenceOnShutdown extends Thread {

  private CloudyRSS cloudyRSS;

  public SavePreferenceOnShutdown(CloudyRSS cloudyRSS) {
    this.cloudyRSS = cloudyRSS;
  }

  public void run() {
    cloudyRSS.writeFeedsConfiguration();
  }
}

/**
 * GUI Application for CloudyRSS
 *
 * @author Andrea Zito <zito.andrea@gmail.com>
 * @version 1.0
 */
public class CloudyRSSGui extends JFrame implements ClipboardOwner {

  /**
   * Mondel/ontroller for the feeds table
   */
  private class FeedTableModelController extends AbstractTableModel
    implements CloudyFeedUpdateHandler, ListSelectionListener
  {
    private CloudyRSS cloudyRSS;

    private String colNames[] = new String[] { "Name", " Description", "Last refresh", "" };
    private ImageIcon iconOK;
    private ImageIcon iconError;
    private ImageIcon iconWait;
    private JTable table;

    public FeedTableModelController(CloudyRSS cloudyRSS) {
      this.cloudyRSS = cloudyRSS;
      this.iconOK = new ImageIcon(CloudyRSSGui.class.getResource("icons/ok.gif"));
      this.iconError = new ImageIcon(CloudyRSSGui.class.getResource("icons/error.gif"));
      this.iconWait = new ImageIcon(CloudyRSSGui.class.getResource("icons/wait.gif"));
    }

    public void setTable(JTable table) {
      this.table = table;
    }

    public int getRowCount() {
      return cloudyRSS.getConfiguredFeeds().length;
    }

    public String getColumnName(int column) {
      return colNames[column];
    }

    public int getColumnCount() {
      return colNames.length;
    }

    public boolean isCellEditable(int row, int column) {
      if (column == 4) {
        return true;
      } else
        return false;
    }
    public Class getColumnClass(int columnIndex) {
      if (columnIndex == 3)
        return ImageIcon.class;
      else return Object.class;
    }

    private CloudyFeedReader getCloudyFeedReaderAtRow(int row) {
      String feeds[] = cloudyRSS.getConfiguredFeeds();
      if (row >= feeds.length) return null;
      Arrays.sort(feeds);
      return cloudyRSS.getCloudyFeedReaderForFeed(feeds[row]);
    }

    public Object getValueAt(int row, int column) {
      CloudyFeedReader feedReader = getCloudyFeedReaderAtRow(row);
      if (feedReader == null) return null;
      switch (column) {
      case 0: return feedReader.getFeedName();
      case 1: return feedReader.getFeedDescription();
      case 2:
        Date d = feedReader.getLastUpdateTimestamp();
        return (d != null)? d : "No update yet";
      case 3:
        if (feedReader.getLastUpdateTimestamp() == null) return iconWait;
        else return (feedReader.hasRefreshedSinceUpdate()) ? iconOK : iconError;
      default:
        return null;
      }
    }

    public void refresh() {
      super.fireTableDataChanged();
    }

    public void notifyUpdate(Date update, CloudyFeedReader reader) {
      super.fireTableDataChanged();
    }

    public void addCloudyFeedReader(CloudyFeedReader r) {
      r.addUpdateHandler(this);
      super.fireTableDataChanged();
    }

    public CloudyFeedReader getSelectedCloudyFeedReader() {
      int row = table.getSelectionModel().getLeadSelectionIndex();
      if (row < 0) return null;
      return getCloudyFeedReaderAtRow(row);
    }

    public void removeSelectedCloudyFeedReader() {
      super.fireTableDataChanged();
    }

    public void valueChanged(ListSelectionEvent e) {
      int row = table.getSelectionModel().getLeadSelectionIndex();
      if (row < 0 || row >= getRowCount()) {
        updateUrlDisplay(null);
      } else {
        CloudyFeedReader feedReader = getCloudyFeedReaderAtRow(row);
        updateUrlDisplay(feedReader.getFeedRssFileName());
      }
    }
  }

  /* ***********************************************************
   *  Instance variables
   *************************************************************/

  private CloudyRSS cloudyRSS;
  private JPanel mainCP;
  private JPanel controlsCP;
  private JTable feedTable;
  private FeedTableModelController feedTableModelController;
  private JTextField urlDisplay;
  private JButton copyURLBtn;
  private JButton removeFeedBtn;
  private String httpServerURL;
  private JFileChooser fileChooser = new JFileChooser();

  public CloudyRSSGui(CloudyRSS cloudyRSS) throws Exception {
    super("CloudyRSS feed configurator");
    this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    this.cloudyRSS = cloudyRSS;

    this.mainCP = new JPanel();
    this.setContentPane(mainCP);

    this.feedTableModelController = new FeedTableModelController(cloudyRSS);
    this.feedTable = new JTable(feedTableModelController);
    this.feedTableModelController.setTable(feedTable);
    this.controlsCP = new JPanel();

    this.httpServerURL = cloudyRSS.getWebServerURL();
    String feeds[] = cloudyRSS.getConfiguredFeeds();
    for (String feed: feeds) {
      feedTableModelController.addCloudyFeedReader(cloudyRSS.getCloudyFeedReaderForFeed(feed));
    }

    initGUI();
  }

  private final void initGUI() {
    mainCP.setLayout(new BorderLayout());
    controlsCP.setLayout(new BoxLayout(controlsCP, BoxLayout.X_AXIS));

    // Setup table
    JScrollPane tableScroll = new JScrollPane(feedTable);
    int lastColumn = feedTable.getColumnModel().getColumnCount();
    feedTable.getColumnModel().getColumn(lastColumn - 1).setMaxWidth(20);
    feedTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    feedTable.getSelectionModel().addListSelectionListener(feedTableModelController);

    //Setup controls
    JButton closeBtn = new JButton("", new ImageIcon(CloudyRSSGui.class.getResource("icons/quit.png")));
    closeBtn.setPreferredSize(new Dimension(32, 32));
    closeBtn.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          quit();
        }
      });

    JButton addFeedBtn = new JButton("", new ImageIcon(CloudyRSSGui.class.getResource("icons/add.png")));
    addFeedBtn.setPreferredSize(new Dimension(32, 32));
    addFeedBtn.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          addNewFeed();
        }
      });

    removeFeedBtn = new JButton("", new ImageIcon(CloudyRSSGui.class.getResource("icons/remove.png")));
    removeFeedBtn.setPreferredSize(new Dimension(32, 32));
    removeFeedBtn.setEnabled(false);
    removeFeedBtn.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          removeFeed();
        }
      });

    // Setup url display
    JPanel displayCP  = new JPanel();
    displayCP.setLayout(new BoxLayout(displayCP, BoxLayout.X_AXIS));

    urlDisplay = new JTextField();
    urlDisplay.setEditable(false);
    updateUrlDisplay(null);

    copyURLBtn = new JButton("", new ImageIcon(CloudyRSSGui.class.getResource("icons/clipboard.png")));
    copyURLBtn.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          copyDisplayedURL();
        }
      });

    displayCP.add(urlDisplay);
    displayCP.add(copyURLBtn);


    // Place controls
    controlsCP.add(addFeedBtn);
    controlsCP.add(removeFeedBtn);
    controlsCP.add(Box.createHorizontalGlue());
    controlsCP.add(closeBtn);

    // Organize panels
    JPanel bottomCP = new JPanel();
    bottomCP.setLayout(new BoxLayout(bottomCP, BoxLayout.Y_AXIS));
    bottomCP.add(displayCP);
    bottomCP.add(controlsCP);

    mainCP.add(tableScroll, BorderLayout.CENTER);
    mainCP.add(bottomCP, BorderLayout.SOUTH);

    // Set dimensions
    this.setSize(500, 300);
  }

  private void updateUrlDisplay(String url) {
    if (url == null) {
      urlDisplay.setText("Select a feed to display it's URL");
      removeFeedBtn.setEnabled(false);
    } else {
      removeFeedBtn.setEnabled(true);
      try {
        urlDisplay.setText(new URL(httpServerURL + "/" + url).toString());
      } catch (MalformedURLException e) {
        urlDisplay.setText("Error computing feed URL");
      }
    }
  }

  public void lostOwnership(Clipboard clipboard, Transferable contents) {
    return;
  }

  private void copyDisplayedURL() {
    StringSelection stringSelection = new StringSelection(urlDisplay.getText());
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    clipboard.setContents(stringSelection, this);
  }

  private void quit() {
    cloudyRSS.writeFeedsConfiguration();
    System.exit(0);
  }

  private void addNewFeed(){
    int ret = fileChooser.showOpenDialog(this);
    if (ret == JFileChooser.APPROVE_OPTION) {
      File file = fileChooser.getSelectedFile();
      URL fileURL;
      try {
        fileURL = file.toURL();
      } catch (MalformedURLException e) {
        JOptionPane.showMessageDialog(this, "Could not open file", "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }

      try {
        CloudyFeedReader reader = cloudyRSS.configureFeed(fileURL);
        if (reader != null) {
          feedTableModelController.addCloudyFeedReader(reader);
        }
      } catch (RuntimeException e) {
        JOptionPane.showMessageDialog(this, "Error configuring cloudyrss feed: " + file.getName(), "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }
    }
  }

  private void removeFeed(){
    CloudyFeedReader r = feedTableModelController.getSelectedCloudyFeedReader();
    if (cloudyRSS.removeFeed(r.getFeedName()))
      feedTableModelController.removeSelectedCloudyFeedReader();
  }

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
      CloudyRSSGui gui = new CloudyRSSGui(cloudyRSS);
      Runtime.getRuntime().addShutdownHook(new SavePreferenceOnShutdown(cloudyRSS));
      gui.setVisible(true);
    } catch (Exception e) {
      System.err.println("Uncatched exception");
      e.printStackTrace();
      System.exit(1);
    }
  }
}
