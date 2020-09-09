package com.mxplay.offlineads.exo.vast.processor;

import com.google.android.exoplayer2.C;
import com.mxplay.offlineads.exo.util.XmlTools;
import com.mxplay.offlineads.exo.vast.model.AdBreak;
import com.mxplay.offlineads.exo.vast.model.VASTModel;
import com.mxplay.offlineads.exo.vast.model.VAST_DOC_ELEMENTS;
import com.mxplay.offlineads.exo.vast.model.VMAPModel;
import com.mxplay.offlineads.exo.vast.model.VMAP_DOC_ELEMENTS;
import java.io.IOException;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class VMAPProcessor {

  public VMAPModel process(String xml) throws Exception {
    VMAPModel vmapModel = new VMAPModel();
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document doc = dBuilder.parse(new InputSource(new StringReader(xml)));
    NodeList adBreaks = doc.getElementsByTagName(VMAP_DOC_ELEMENTS.adbreak.getValue());
    for (int i = 0; i < adBreaks.getLength(); ++i) {
      Node adBreak = adBreaks.item(i);
      Node vastAdSource = XmlTools
          .findChildNode(adBreak, VMAP_DOC_ELEMENTS.vastAdSource.getValue());
      if (vastAdSource == null) {
        continue;
      }
      Node vastData = XmlTools.findChildNode(vastAdSource, VMAP_DOC_ELEMENTS.vastAdData.getValue());
      if (vastData != null) {
        Node vast = XmlTools.findChildNode(vastData, VAST_DOC_ELEMENTS.vast.getValue());
        if (vast != null) {
          VASTModel vastModel = new VASTModel(XmlTools.nodeToDocument(vastData));
          AdBreak adBreakModel = new AdBreak(
              adBreak.getAttributes().getNamedItem("timeOffset").getNodeValue(),
              vastModel);
          if (adBreakModel.getStartTime() != C.TIME_UNSET){
            vmapModel.addABreak(adBreakModel);
          }

        }
      }
    }
    return vmapModel;
  }
}
