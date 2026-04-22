# qclass-cycle-guard-maven-plugin

A Maven plugin that detects cyclic dependencies between QueryDSL Q-classes and generates `META-INF/cyclic-qclasses.txt` to expose them at runtime.

## Problem

QueryDSL generates Q-classes with static fields referencing other Q-classes. When circular references exist (e.g., QOrder → QCustomer → QOrder), multi-threaded class initialization can cause JVM-level deadlocks that are extremely hard to diagnose.

## Solution

This plugin runs at **build time** to:
1. Scan generated Q-class source files and build a dependency graph
2. Detect cycles using Tarjan's Strongly Connected Components algorithm
3. Write `META-INF/cyclic-qclasses.txt` listing all cyclic Q-class FQCNs for runtime discovery

## Quick Start

### 1. Add JitPack repository

**pom.xml**
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<pluginRepositories>
    <pluginRepository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </pluginRepository>
</pluginRepositories>
```

### 2. Add plugin

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.github.jsjg73</groupId>
            <artifactId>qclass-cycle-guard-maven-plugin</artifactId>
            <version>0.3.0</version>
            <executions>
                <execution>
                    <goals>
                        <goal>detect</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 3. Build

```bash
mvn package
```

The plugin automatically detects cycles and writes `META-INF/cyclic-qclasses.txt` into the build output (`target/classes`).

### 4. Use the resource at runtime

```java
Enumeration<URL> resources = getClass().getClassLoader()
    .getResources("META-INF/cyclic-qclasses.txt");
// read FQCNs and load them on a single thread before multi-threaded startup
```

## Build Lifecycle

The `detect` goal binds to the `process-classes` phase by default, which runs after `compile` (and therefore after QueryDSL's annotation processing).

```
generate-sources (QueryDSL APT → Q-class 생성)
    ↓
compile
    ↓
process-classes  ←  detect goal 실행
```

## Configuration

All parameters are optional. The defaults work with standard QueryDSL + Maven setups.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `qclass.sourceDirs` | `${project.build.directory}/generated-sources/annotations` | Q-class 소스가 생성되는 디렉토리. 여러 경로를 지정할 수 있음 |
| `qclass.outputDirectory` | `${project.build.outputDirectory}` | `META-INF/cyclic-qclasses.txt`가 생성될 디렉토리 |

### Custom source directory

QueryDSL APT 경로가 기본값과 다를 경우:

```xml
<plugin>
    <groupId>com.github.jsjg73</groupId>
    <artifactId>qclass-cycle-guard-maven-plugin</artifactId>
    <version>0.3.0</version>
    <executions>
        <execution>
            <goals>
                <goal>detect</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <sourceDirs>
            <sourceDir>${project.build.directory}/generated-sources/apt</sourceDir>
            <sourceDir>${project.build.directory}/generated-sources/annotations</sourceDir>
        </sourceDirs>
    </configuration>
</plugin>
```

## How It Works

1. **Scan** — Q로 시작하는 `.java` 파일을 읽어 `new QXxx()` 생성자 호출 패턴을 파싱
2. **Detect** — 의존 그래프를 구성하고 Tarjan's SCC 알고리즘으로 순환 탐지
3. **Resource** — `META-INF/cyclic-qclasses.txt`에 FQCN을 한 줄씩, 알파벳 순 기록

## Build Output

빌드 로그에서 순환 감지 결과를 확인할 수 있습니다:

```
[INFO] Q-class 2개 스캔, 의존 관계 2개 발견
[WARNING] Q-class 순환 감지: QCustomer <-> QOrder
[INFO] 리소스 생성됨: .../target/classes/META-INF/cyclic-qclasses.txt
```

생성된 리소스 파일:

```
com.example.QCustomer
com.example.QOrder
```

## Compatibility

- Java 11+
- Maven 3.6+
- QueryDSL 4.x / 5.x

## Sample Project

[qclass-cycle-guard-samples/maven-sample](https://github.com/jsjg73/qclass-cycle-guard-samples/tree/main/maven-sample) — 동작하는 예제 프로젝트

## Related

- [qclass-cycle-guard-core](https://github.com/jsjg73/qclass-cycle-guard-core) — 핵심 알고리즘 (build-tool 독립)
- [qclass-cycle-guard-plugin](https://github.com/jsjg73/qclass-cycle-guard-plugin) — Gradle 플러그인

## License

[Apache License 2.0](LICENSE)
