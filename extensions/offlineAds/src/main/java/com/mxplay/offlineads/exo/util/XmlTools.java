//
//  XmlTools.java
//
//  Copyright (c) 2014 Nexage. All rights reserved.
//

package com.mxplay.offlineads.exo.util;

import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XmlTools {
	
	private static String TAG = "XmlTools";
	
	public static void logXmlDocument(Document doc) throws TransformerException {
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer
				.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(
				"{http://xml.apache.org/xslt}indent-amount", "4");
		StringWriter sw = new StringWriter();
		transformer.transform(new DOMSource(doc), new StreamResult(sw));
	}

	public static String xmlDocumentToString(Document doc) throws TransformerException {
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer
				.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(
				"{http://xml.apache.org/xslt}indent-amount", "4");

		StringWriter sw = new StringWriter();
		transformer.transform(new DOMSource(doc), new StreamResult(sw));
		return sw.toString();
	}

	public static String xmlDocumentToString(Node node) throws TransformerException {
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer
				.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(
				"{http://xml.apache.org/xslt}indent-amount", "4");

		StringWriter sw = new StringWriter();
		transformer.transform(new DOMSource(node), new StreamResult(sw));

		return sw.toString();
	}
	public static Document stringToDocument(String doc)
			throws ParserConfigurationException, IOException, SAXException {
		DocumentBuilder db;
		db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		InputSource is = new InputSource();
		is.setCharacterStream(new StringReader(doc));
		return db.parse(is);
	}

	public static Document nodeToDocument(Node node) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document newDocument = builder.newDocument();
		Node importedNode = newDocument.importNode(node, true);
		newDocument.appendChild(importedNode);
		return newDocument;
	}

	public static String stringFromStream(InputStream inputStream)
			throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length = 0;

		while ((length = inputStream.read(buffer)) != -1) {
			baos.write(buffer, 0, length);
		}

		byte[] bytes = baos.toByteArray();

		return new String(bytes, "UTF-8");
	}

	public static String getElementValue(Node node) {
		NodeList childNodes = node.getChildNodes();
		Node child;
		String value = null;
		CharacterData cd;

		for (int childIndex = 0; childIndex < childNodes.getLength(); childIndex++) {
			child = childNodes.item(childIndex);
			// value = child.getNodeValue().trim();
			if (child instanceof CharacterData){
				cd = (CharacterData) child;
				value = cd.getData().trim();
				if (value.length() == 0) {
					// this node was whitespace
					continue;
				}
			}else {
				continue;
			}
			return value;

		}
		return value;
	}


	@Nullable
	public static Node findChildNode(Node root, String name) {
		if (root != null && name != null) {
			NodeList childNodes = root.getChildNodes();
			if (childNodes == null) {
				return null;
			} else {
				for(int i = 0; i < childNodes.getLength(); ++i) {
					Node node = childNodes.item(i);
					if (name.equalsIgnoreCase(node.getNodeName())) {
						return node;
					}
				}

				return null;
			}
		} else {
			return null;
		}
	}


}
