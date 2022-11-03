# This repository is archived. Please migrate to [emarsys-scala-akka-sdk](https://github.com/emartech/emarsys-scala-akka-sdk/releases).

# emarsys-client-scala-sdk

## Usage

### `0.4.10` and above

Add the following to `build.sbt`:

```
libraryDependencies += "com.emarsys" %% "emasys-client-scala-sdk" % "x.y.z"
```

The latest released version is on the maven badge at the top of this document.

If you need some functionality that is not released yet, you can depend on the snapshot release. Every push to master will be released as a snapshot, you can find the exact version in the [build output] under the `Release` stage.

To depend on a snapshot, include the following in your `build.sbt`
```scala
resolvers += Resolver.sonatypeRepo("snapshots")
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

Choose the appropriate version number according to [semver] then create and push a tag with it, prefixed with `v`.
For example:

```
$ git tag -s v1.0.3
$ git push --tag
```

After pushing the tag, while it is not strictly necessary, please [draft a release on github] with this tag too.

[build output]: https://travis-ci.org/emartech/emarsys-client-scala-sdk
[semver]: https://semver.org
[draft a release on github]: https://github.com/emartech/emarsys-client-scala-sdk/releases/new
