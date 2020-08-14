package pro.akvel.spring.converter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

class XmlScannerTest {

    private static final Path root = Paths.get(".").normalize().toAbsolutePath();

    @Test
    public void shouldAllFindXmls() {
        XmlScanner scanner = new XmlScanner(root + "/src/test/resources/pro/akvel/spring/converter/xml");
        List<File> files = scanner.getConfigurations();

        Assertions.assertEquals(2, files.size());
        Assertions.assertTrue(files.stream().anyMatch(it -> it.getName().equals("1.xml")));
        Assertions.assertTrue(files.stream().anyMatch(it -> it.getName().equals("2.xml")));
    }


    @Test
    public void shouldReturnEmpty() {
        XmlScanner scanner = new XmlScanner(root + "/src/test/resources/pro/akvel/spring/converter/xml/empty");
        List<File> files = scanner.getConfigurations();

        Assertions.assertTrue(files.isEmpty());
    }

    @Test
    public void shouldReturnErrorIfFolderNotFound() {
        XmlScanner scanner = new XmlScanner(root + "/src/test/resources/pro/akvel/spring/converter/xml/unknown");

        Assertions.assertThrows(IllegalArgumentException.class, scanner::getConfigurations);
    }
}