/*
 *  Copyright (c) 2011 Andrea Zito
 *
 *  This is free software; see lgpl-2.1.txt
 */
package cloudyrss;

/**
 * Exception thrown by CloudyRSS
 *
 * @author Andrea Zito <zito.andrea@gmail.com>
 * @version 1.0
 */
public class CloudyRSSException extends RuntimeException {

  public CloudyRSSException(String message, Throwable cause) {
    super(message, cause);
  }

  public CloudyRSSException(String message) {
    super(message);
  }

  public CloudyRSSException() {}
}
