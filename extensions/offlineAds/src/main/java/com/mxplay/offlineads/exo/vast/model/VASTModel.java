//
//  VASTModel.java
//
//  Copyright (c) 2014 Nexage. All rights reserved.
//

package com.mxplay.offlineads.exo.vast.model;

import com.mxplay.offlineads.exo.util.XmlTools;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VASTModel implements Serializable {

  private static final long serialVersionUID = 4318368258447283733L;
  private transient Document vastsDocument;
  // Ad xpath expression
  private static final String adXPATH = "//Ad";

  public VASTModel(Document vast) {
    this.vastsDocument = vast;
  }

  public List<VastAdModel> getAdsList() throws Exception {
    List<VastAdModel> vastAdModels = new ArrayList<>();
    XPath xpath = XPathFactory.newInstance().newXPath();
    NodeList nodes = (NodeList) xpath.evaluate(adXPATH,
        vastsDocument, XPathConstants.NODESET);
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      vastAdModels.add(new VastAdModel(XmlTools.nodeToDocument(node)));
    }
    return vastAdModels;

  }

}
