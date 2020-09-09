package com.mxplay.offlineads.exo.vast.model;

import android.text.TextUtils;
import android.util.Log;
import com.mxplay.offlineads.exo.util.DateTimeUtils;
import com.mxplay.offlineads.exo.util.XmlTools;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VastAdModel {

  // Ad xpath expression
  private static final String titleXPATH = "//AdTitle";
  private static final String descXPATH = "//Description";
  private static final String durationXPATH = "//Duration";
  private static final String mediaFilesXPATH = "//MediaFile";
  public static final String TAG = "VastAdModel";


  //AdTitle
  //Description
  //Duration
  //MediaFiles

  private transient Document document;

  public VastAdModel(Document document) {
    this.document = document;
  }

  public String getAdId() throws RuntimeException {
    try {
      NodeList node = document.getElementsByTagName("Ad");
      Node id = node.item(0).getAttributes().getNamedItem("id");
      return id.getNodeValue();
    } catch (Exception ignored) {
    }
    return null;
  }

  public String getAdvertiserName() {
    return null;
  }

  public boolean isSkippable() {
    return false;
  }

  public double getSkipTimeOffset() {
    return 0;
  }

  public String getTitle() {
    return getNodeValue(titleXPATH);
  }

  public String getDescription() {
    return getNodeValue(descXPATH);
  }

  public long getDuration() {
    String nodeValue = getNodeValue(durationXPATH);
    if (!TextUtils.isEmpty(nodeValue)) {
      return DateTimeUtils.getTimeInMillis(nodeValue)/1000;
    }
    return 0;
  }

  public String getMediaUrl() {
    return getNodeValue(mediaFilesXPATH);
  }

  private String getNodeValue(String xPath) {
    XPath xpath = XPathFactory.newInstance().newXPath();
    try {
      NodeList nodes = (NodeList) xpath.evaluate(xPath,
          document, XPathConstants.NODESET);
      if (nodes != null) {
        if (nodes.getLength() > 0) {
          return XmlTools.getElementValue(nodes.item(0));
        }
      }
    } catch (Exception e) {
      Log.e(TAG, e.getMessage(), e);
    }
    return "";
  }


}
