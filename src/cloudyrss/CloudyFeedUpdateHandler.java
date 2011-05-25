/*
 *  Copyright (c) 2011 Andrea Zito
 *
 *  This is free software; see lgpl-2.1.txt
 */
package cloudyrss;

import java.util.Date;

/**
 * Interface used by CloudyFeedReader to notify updates
 *
 * @author Andrea Zito <zito.andrea@gmail.com>
 * @version 1.0
 */
public interface CloudyFeedUpdateHandler {

  /**
   * Notifies the update of the specified feed reader
   *
   * @param update Timestamp of the update
   * @param reader Update CloudyFeedReader instance
   */
  public void notifyUpdate(Date update, CloudyFeedReader reader);

}
