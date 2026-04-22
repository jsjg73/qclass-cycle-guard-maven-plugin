package io.github.jsjg73.qclasscycleguard.maven;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QClassCycleGuardMojoTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Q-class가 없으면 빈 리소스 파일이 생성된다")
    void emptyOutputWhenNoQClasses() throws Exception {
        QClassCycleGuardMojo mojo = new QClassCycleGuardMojo();
        setField(mojo, "sourceDirs", List.of(tempDir.toFile()));
        setField(mojo, "outputDirectory", tempDir.toFile());
        setField(mojo, "defaultAnnotationProcessingDir", tempDir.toFile());

        mojo.execute();

        Path resourceFile = tempDir.resolve("META-INF/cyclic-qclasses.txt");
        assertThat(resourceFile).exists();
        assertThat(Files.readString(resourceFile)).isEmpty();
    }

    @Test
    @DisplayName("순환이 없는 Q-class는 빈 리소스 파일을 생성한다")
    void noCyclesProducesEmptyFile() throws Exception {
        writeQClass("com/example", "QOrder", "");
        writeQClass("com/example", "QCustomer", "");

        QClassCycleGuardMojo mojo = new QClassCycleGuardMojo();
        setField(mojo, "sourceDirs", List.of(tempDir.toFile()));
        setField(mojo, "outputDirectory", tempDir.toFile());
        setField(mojo, "defaultAnnotationProcessingDir", tempDir.toFile());

        mojo.execute();

        Path resourceFile = tempDir.resolve("META-INF/cyclic-qclasses.txt");
        assertThat(Files.readString(resourceFile)).isEmpty();
    }

    @Test
    @DisplayName("순환이 있는 Q-class는 FQCN이 리소스 파일에 기록된다")
    void cyclesAreWrittenToResourceFile() throws Exception {
        writeQClass("com/example", "QOrder",
            "public final QCustomer customer = new QCustomer(forProperty(\"customer\"));");
        writeQClass("com/example", "QCustomer",
            "public final QOrder order = new QOrder(forProperty(\"order\"));");

        QClassCycleGuardMojo mojo = new QClassCycleGuardMojo();
        setField(mojo, "sourceDirs", List.of(tempDir.toFile()));
        setField(mojo, "outputDirectory", tempDir.toFile());
        setField(mojo, "defaultAnnotationProcessingDir", tempDir.toFile());

        mojo.execute();

        Path resourceFile = tempDir.resolve("META-INF/cyclic-qclasses.txt");
        List<String> lines = Files.readAllLines(resourceFile);
        assertThat(lines).containsExactlyInAnyOrder(
            "com.example.QCustomer",
            "com.example.QOrder"
        );
    }

    // ── 헬퍼 ──

    private void writeQClass(String packagePath, String className, String body) throws IOException {
        Path dir = tempDir.resolve(packagePath);
        Files.createDirectories(dir);
        String pkg = packagePath.replace("/", ".");
        String source = "package " + pkg + ";\n"
            + "public class " + className + " {\n"
            + body + "\n"
            + "}\n";
        Files.writeString(dir.resolve(className + ".java"), source);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
