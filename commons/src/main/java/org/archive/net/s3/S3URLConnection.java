package org.archive.net.s3;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.security.AWSCredentials;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.S3ServiceException;

/**
 * URLConnection for Amazon S3 objects.
 *
 * @author jlee
 */
public class S3URLConnection extends URLConnection {
  String id     = "";
  String secret = "";

  S3Object object = null;
  
 /**
  * Contruct a new S3URLConnection.
  *
  * @param a URL of the form s3://id:secret@bucket/key
  */
  public S3URLConnection(URL url) {
    super(url);
    
    String[] userInfo = url.getUserInfo().split(":");
    
    if (userInfo != null) {
      id     = userInfo[0];
      secret = userInfo[1];
    }
  }
  
 /**
  * Connect to S3 and get the object reference, but don't read any of
  * the object data yet.
  */
  public void connect() throws IOException {
    try {
      AWSCredentials credentials = new AWSCredentials(id, secret);

      RestS3Service service = new RestS3Service(credentials);

      S3Bucket bucket = new S3Bucket(url.getHost());

      object = service.getObject(bucket, url.getPath().substring(1));
    } catch (S3ServiceException s3e) {
      s3e.printStackTrace();
      throw new IOException("Error connecting to S3: " + s3e, s3e);
    }
  }
  
 /**
  * XXX Not sure what this should be or if it even matters for our use.
  *
  * @return the made up content type "arc"
  */
  public String getContentType() {
    return "arc";
  }
  
 /**
  * Get an InputStream for the object, connecting to S3 if connect()
  * hasn't been called yet.
  *
  * @return InputStream for the S3 object
  */
  public InputStream getInputStream() throws IOException {
    try {
      if (! connected) {
        connect();
      }
      
      return object.getDataInputStream();
    }
    catch (S3ServiceException s3e) {
      s3e.printStackTrace();
      throw new IOException("Error reading from S3: " + s3e, s3e);
    }
  }
}
