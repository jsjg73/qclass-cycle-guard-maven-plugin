package io.github.jsjg73.qclasscycleguard.maven;

import io.github.jsjg73.qclasscycleguard.core.CyclicQClassResourceWriter;
import io.github.jsjg73.qclasscycleguard.core.QClassScanner;
import io.github.jsjg73.qclasscycleguard.core.TarjanScc;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Q-class 순환 참조를 감지하고 META-INF/cyclic-qclasses.txt를 생성하는 Maven Mojo.
 *
 * <p>QueryDSL annotation processing 이후 실행되어 생성된 Q-class 소스를 스캔하고,
 * 순환 참조가 있으면 경고 로그를 출력하며 리소스 파일을 생성한다.</p>
 *
 * <h2>사용법</h2>
 * <pre>
 * &lt;!-- pom.xml --&gt;
 * &lt;plugin&gt;
 *   &lt;groupId&gt;com.github.jsjg73&lt;/groupId&gt;
 *   &lt;artifactId&gt;qclass-cycle-guard-maven-plugin&lt;/artifactId&gt;
 *   &lt;version&gt;v0.3.0&lt;/version&gt;
 *   &lt;executions&gt;
 *     &lt;execution&gt;
 *       &lt;goals&gt;&lt;goal&gt;detect&lt;/goal&gt;&lt;/goals&gt;
 *     &lt;/execution&gt;
 *   &lt;/executions&gt;
 * &lt;/plugin&gt;
 * </pre>
 */
@Mojo(name = "detect", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class QClassCycleGuardMojo extends AbstractMojo {

    /**
     * QueryDSL이 Q-class를 생성하는 디렉토리들.
     * 기본값은 Maven annotation processing 표준 경로와 QueryDSL apt 경로.
     */
    @Parameter(property = "qclass.sourceDirs")
    private List<File> sourceDirs;

    /**
     * META-INF/cyclic-qclasses.txt가 생성될 디렉토리.
     * 기본값은 Maven 클래스 출력 디렉토리.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "qclass.outputDirectory", required = true)
    private File outputDirectory;

    /**
     * annotation processing으로 생성된 소스 디렉토리.
     * sourceDirs가 지정되지 않은 경우 이 경로를 사용한다.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/annotations", readonly = true)
    private File defaultAnnotationProcessingDir;

    @Override
    public void execute() throws MojoExecutionException {
        List<File> dirs = resolveSourceDirs();

        QClassScanner scanner = new QClassScanner();
        try {
            scanner.scan(dirs);
        } catch (IOException e) {
            throw new MojoExecutionException("Q-class 스캔 중 오류 발생", e);
        }

        getLog().info(String.format("Q-class %d개 스캔, 의존 관계 %d개 발견",
            scanner.getScannedCount(), scanner.getDependencyCount()));

        List<Set<String>> cycles = new TarjanScc(scanner.getDependencyGraph()).findCycles();

        if (cycles.isEmpty()) {
            getLog().info("Q-class 순환 참조 없음.");
        } else {
            for (Set<String> scc : cycles) {
                getLog().warn("Q-class 순환 감지: " + String.join(" <-> ", scc));
            }
        }

        try {
            Path resourceFile = new CyclicQClassResourceWriter(cycles, scanner.getInfoMap())
                .write(outputDirectory.toPath());
            getLog().info("리소스 생성됨: " + resourceFile);
        } catch (IOException e) {
            throw new MojoExecutionException("리소스 파일 생성 중 오류 발생", e);
        }
    }

    private List<File> resolveSourceDirs() {
        if (sourceDirs != null && !sourceDirs.isEmpty()) {
            return sourceDirs;
        }
        List<File> defaults = new ArrayList<>();
        defaults.add(defaultAnnotationProcessingDir);
        return defaults;
    }
}
