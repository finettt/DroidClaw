package io.finett.droidclaw.navigation;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertTrue;

public class NavigationLocalizationTest {

    private static File resourceFile(String relativePath) {
        File fromModule = new File("src/main/res/" + relativePath);
        if (fromModule.isFile()) return fromModule;
        File fromRoot = new File("app/src/main/res/" + relativePath);
        assertTrue("cannot locate resource " + relativePath, fromRoot.isFile());
        return fromRoot;
    }

    private static Document parse(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(file);
    }

    private static Set<String> stringNames(File file) throws Exception {
        Set<String> names = new HashSet<>();
        NodeList strings = parse(file).getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            names.add(((Element) strings.item(i)).getAttribute("name"));
        }
        return names;
    }

    @Test
    public void destinationLabelsUseLocalizedStringResources() throws Exception {
        Document navigation = parse(resourceFile("navigation/nav_graph.xml"));
        Set<String> defaultStrings = stringNames(resourceFile("values/strings.xml"));
        Set<String> russianStrings = stringNames(resourceFile("values-ru/strings.xml"));
        NodeList nodes = navigation.getElementsByTagName("*");
        int labeledDestinations = 0;

        for (int i = 0; i < nodes.getLength(); i++) {
            Element fragment = (Element) nodes.item(i);
            Node labelNode = fragment.getAttributeNodeNS(
                    "http://schemas.android.com/apk/res/android", "label");
            if (labelNode == null || labelNode.getNodeValue().isEmpty()) continue;

            labeledDestinations++;
            String label = labelNode.getNodeValue();
            String id = fragment.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "id");
            assertTrue(id + " has a hardcoded label: " + label,
                    label.startsWith("@string/"));
            String stringName = label.substring("@string/".length());
            assertTrue(label + " is missing from default strings", defaultStrings.contains(stringName));
            assertTrue(label + " is missing from Russian strings", russianStrings.contains(stringName));
        }

        assertTrue("navigation graph has no labeled destinations", labeledDestinations > 0);
    }

    @Test
    public void russianStringsCoverAllTranslatableDefaultStrings() throws Exception {
        Document defaults = parse(resourceFile("values/strings.xml"));
        Set<String> russianStrings = stringNames(resourceFile("values-ru/strings.xml"));
        Set<String> missing = new TreeSet<>();
        NodeList strings = defaults.getElementsByTagName("string");

        for (int i = 0; i < strings.getLength(); i++) {
            Element string = (Element) strings.item(i);
            if (!"false".equals(string.getAttribute("translatable"))
                    && !russianStrings.contains(string.getAttribute("name"))) {
                missing.add(string.getAttribute("name"));
            }
        }

        assertTrue("Russian strings are missing translations for: " + missing, missing.isEmpty());
    }

}
