package pro.akvel.spring.converter.metadata;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaConfigurationMetadataBuilderTest {

    private static final Path root = Paths.get(".").normalize().toAbsolutePath();

    @Test
    public void getMetadata() {
        String path = root + "/src/test/resources/pro/akvel/spring/converter/metadata/";
        var metadata =  JavaConfigurationMetadataBuilder
                .getMetadata(path + "configFile.xml",
                        path,
                        "pro.akvel.test.xml",
                        true);

        assertEquals("ConfigFile.java", metadata.getJavaConfigFileClassName());
        assertEquals("pro.akvel.test.xml", metadata.getPackageName());
    }

    @Test
    public void getMetadataWithAddFilePath() {
        String path = root + "/src/test/resources/pro/akvel/spring/converter/metadata/";
        var metadata =  JavaConfigurationMetadataBuilder
                .getMetadata(path + "/test/configFile.xml",
                        path,
                        "pro.akvel.test.xml",
                        true);

        assertEquals("ConfigFile.java", metadata.getJavaConfigFileClassName());
        assertEquals("pro.akvel.test.xml.test", metadata.getPackageName());
    }

    @Test
    public void getMetadataWithoutAddFilePath() {
        String path = root + "/src/test/resources/pro/akvel/spring/converter/metadata/";
        var metadata =  JavaConfigurationMetadataBuilder
                .getMetadata(path + "/test/configFile.xml",
                        path,
                        "pro.akvel.test.xml",
                        false);

        assertEquals("ConfigFile.java", metadata.getJavaConfigFileClassName());
        assertEquals("pro.akvel.test.xml", metadata.getPackageName());
    }


    @ParameterizedTest
    @CsvSource(value = {
            "test.xml:Test.java",
            "tEst.xml:TEst.java",
            "test:Test.java",
            "test.:Test.java",
            "te,st-te_st test.xml:TeStTeStTest.java",
            "TestConfig.xml:TestConfig.java"
    },
            delimiter = ':')
    public void getClassName(String fileName, String expectedClassName) {
        assertEquals(expectedClassName,
                JavaConfigurationMetadataBuilder.getClassName(fileName));
    }

    @Test
    public void getClassNameWrong() {
        Assertions.assertThrows(Exception.class, () ->
                JavaConfigurationMetadataBuilder.getClassName("."))
        ;
    }


    private static final String BASE_PACKAGE = "pro.akvel.test.conf";

    @ParameterizedTest
    @MethodSource("getPackageNameProvideArgs")
    public void getPackageName(String filePath,
                               String configBasePath,
                               String expectedPackageName) {
        assertEquals(expectedPackageName,
                JavaConfigurationMetadataBuilder.getPackageName(BASE_PACKAGE,
                        filePath,
                        configBasePath
                )
        );
    }

    @Test
    public void getPackageFilePathNotAbsolute() {
        Assertions.assertThrows(Exception.class, () ->
                JavaConfigurationMetadataBuilder.getPackageName(BASE_PACKAGE,
                        "./test",
                        "/test"
                )
        );
    }

    @Test
    public void getPackageBasePathNotAbsolute() {
        Assertions.assertThrows(Exception.class, () ->
                JavaConfigurationMetadataBuilder.getPackageName(BASE_PACKAGE,
                        "/test",
                        "./test"
                )
        );
    }

    private static Stream<Arguments> getPackageNameProvideArgs() {
        return Stream.of(
                Arguments.of("C:\\Dir\\test-test\\my pac", "C:\\Dir\\",
                        BASE_PACKAGE + ".test_test.my_pac"),
                Arguments.of("/dir/test test", "/dir",
                        BASE_PACKAGE + ".test_test"),
                Arguments.of("/dir/test test", "/dir/test test",
                        BASE_PACKAGE
                )
        );
    }

}