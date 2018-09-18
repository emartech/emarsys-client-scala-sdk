[![Maven Central](https://img.shields.io/maven-central/v/com.emarsys/emarsys-client-scala-sdk_2.12.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.emarsys%22%20AND%20a:%22emarsys-client-scala-sdk_2.12%22)

# emarsys-client-scala-sdk

## Usage

### `0.4.10` and above

Add the following to `build.sbt`:

```
libraryDependencies += "com.emarsys" %% "emasys-client-scala-sdk" % "0.4.10"
```


### Prior to `0.4.10`

Add the following to `build.sbt`:

```
resolvers += "emarsys-client-scala-sdk on GitHub" at "https://raw.github.com/emartech/emarsys-client-scala-sdk/master/releases"
```
```
libraryDependencies += "com.emarsys" % "emarsys-client-scala-sdk" % "0.4.9"
```

## Creating a release

This library is using [sbt-release-early] for releasing artifacts. Every push will be released to maven central, see the plugins documentation on the versioning schema.

### To cut a final release:

Choose the appropriate version number according to [semver] then create and push a tag with it, prefixed with `v`.
For example:

```
$ git tag -a v1.0.3
$ git push --tag
```

After pushing the tag, while it is not strictly necessary, please [draft a release on github] with this tag too.


[sbt-release-early]: https://github.com/scalacenter/sbt-release-early
[semver]: https://semver.org
[draft a release on github]: https://github.com/emartech/emarsys-client-scala-sdk/releases/new
