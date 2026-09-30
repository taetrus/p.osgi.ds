package com.kk.pde2tycho.xml;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** The few DOM operations the tool needs, on the JDK's own XML stack. */
public final class Xml {

	private Xml() {
	}

	/** Parses a workspace file. DOCTYPEs are refused: none of the PDE formats use one. */
	public static Document parse(Path file) throws IOException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setExpandEntityReferences(false);
			return factory.newDocumentBuilder().parse(file.toFile());
		} catch (ParserConfigurationException | SAXException e) {
			throw new IOException("Cannot parse " + file + ": " + e.getMessage(), e);
		}
	}

	public static List<Element> children(Element parent) {
		List<Element> out = new ArrayList<>();
		NodeList nodes = parent.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (node instanceof Element) {
				out.add((Element) node);
			}
		}
		return out;
	}

	public static List<Element> children(Element parent, String tag) {
		return children(parent).stream().filter(e -> e.getTagName().equals(tag)).toList();
	}

	/** The element as XML text without a declaration, e.g. a target location copied verbatim. */
	public static String toString(Element element) {
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StringWriter out = new StringWriter();
			transformer.transform(new DOMSource(element), new StreamResult(out));
			return out.toString();
		} catch (TransformerException e) {
			throw new IllegalStateException("Cannot serialise <" + element.getTagName() + ">", e);
		}
	}

	public static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
