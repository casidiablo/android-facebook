This is a stand-alone implementation of the [official Facebook library for Android][1]. You could want to use
this library if you do not want to deal with [Android Libraries][2], but instead use a JAR directly or
through Maven.

###Download

Latest version available is `1.5`:

- [Download JAR][3]
- [Download Sources][4]
- [Download Javadoc][5]

###Maven

You can use it from Maven by including this dependency and repository:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    ...
    <dependencies>
        ...
        <dependency>
            <groupId>com.codeslap</groupId>
            <artifactId>android-facebook</artifactId>
            <version>1.5</version>
            <scope>compile</scope>
        </dependency>
    </dependencies>

    <repositories>
        <repository>
            <id>codeslap</id>
            <url>http://casidiablo.github.com/codeslap-maven/repository/</url>
        </repository>
    </repositories>
</project>
```

  [1]: https://github.com/facebook/facebook-android-sdk
  [2]: http://developer.android.com/guide/developing/projects/index.html#LibraryProjects
  [3]: http://casidiablo.github.com/codeslap-maven/repository/com/codeslap/android-facebook/1.5/android-facebook-1.5.jar
  [4]: http://casidiablo.github.com/codeslap-maven/repository/com/codeslap/android-facebook/1.5/android-facebook-1.5-sources.jar
  [5]: http://casidiablo.github.com/codeslap-maven/repository/com/codeslap/android-facebook/1.5/android-facebook-1.5-javadoc.jar